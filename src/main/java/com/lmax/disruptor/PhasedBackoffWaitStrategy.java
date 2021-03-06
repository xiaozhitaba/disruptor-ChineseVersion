/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;

/**
 * <p>Phased wait strategy for waiting {@link EventProcessor}s on a barrier.</p>
 *
 * <p>This strategy can be used when throughput and low-latency are not as important as CPU resource.
 * Spins, then yields, then waits using the configured fallback WaitStrategy.</p>
 */
/**
 * <p>{@link EventProcessor}的分阶段等待策略</p>
 * 
 * <p>当吞吐量和低延迟不如CPU资源那么重要时，可以使用此策略; PhasedBackoffWaitStrategy的实现方法是先自旋(10000次),不行再临时让出调度(yield),
 *	不行再使用其他的策略进行等待; 可以根据具体场景自行设置自旋时间、yield时间和备用等待策略</p>
 */
public final class PhasedBackoffWaitStrategy implements WaitStrategy
{
    private static final int SPIN_TRIES = 10000;
    private final long spinTimeoutNanos;
    private final long yieldTimeoutNanos;
    private final WaitStrategy fallbackStrategy;

    public PhasedBackoffWaitStrategy(
        long spinTimeout,
        long yieldTimeout,
        TimeUnit units,
        WaitStrategy fallbackStrategy)
    {
        this.spinTimeoutNanos = units.toNanos(spinTimeout);
        this.yieldTimeoutNanos = spinTimeoutNanos + units.toNanos(yieldTimeout);
        this.fallbackStrategy = fallbackStrategy;
    }

	/**
	 *	构建{@link PhasedBackoffWaitStrategy}并回退到{@link BlockingWaitStrategy}
	 *
	 * @param spinTimeout  繁忙旋转的最长时间
	 * @param yieldTimeout 收益的最长时间
	 * @param units        timeout的时间单位
	 * @return 构建的等待策略
	 */
    public static PhasedBackoffWaitStrategy withLock(
        long spinTimeout,
        long yieldTimeout,
        TimeUnit units)
    {
        return new PhasedBackoffWaitStrategy(
            spinTimeout, yieldTimeout,
            units, new BlockingWaitStrategy());
    }

	/**
	 *	构建 {@link PhasedBackoffWaitStrategy}并回退到{@link LiteBlockingWaitStrategy}
	 *
	 * @param spinTimeout  繁忙旋转的最长时间
	 * @param yieldTimeout 收益的最长时间
	 * @param units        timeout的单位
	 * @return 构建的等待策略
	 */
    public static PhasedBackoffWaitStrategy withLiteLock(
        long spinTimeout,
        long yieldTimeout,
        TimeUnit units)
    {
        return new PhasedBackoffWaitStrategy(
            spinTimeout, yieldTimeout,
            units, new LiteBlockingWaitStrategy());
    }

	/**
	 *	构建 {@link PhasedBackoffWaitStrategy} 并回退到 {@link SleepingWaitStrategy}
	 *
	 * @param spinTimeout  繁忙旋转的最长时间
	 * @param yieldTimeout 收益的最长时间
	 * @param units        timeout的单位
	 * @return 构建的等待策略
	 */
    public static PhasedBackoffWaitStrategy withSleep(
        long spinTimeout,
        long yieldTimeout,
        TimeUnit units)
    {
        return new PhasedBackoffWaitStrategy(
            spinTimeout, yieldTimeout,
            units, new SleepingWaitStrategy(0));
    }

    @Override
    public long waitFor(long sequence, Sequence cursor, Sequence dependentSequence, SequenceBarrier barrier)
        throws AlertException, InterruptedException, TimeoutException
    {
        long availableSequence;
        long startTime = 0;
        int counter = SPIN_TRIES;

        do
        {
            if ((availableSequence = dependentSequence.get()) >= sequence)
            {
                return availableSequence;
            }

            if (0 == --counter)
            {
                if (0 == startTime)
                {
                    startTime = System.nanoTime();
                }
                else
                {
                    long timeDelta = System.nanoTime() - startTime;
                    if (timeDelta > yieldTimeoutNanos)
                    {
                        return fallbackStrategy.waitFor(sequence, cursor, dependentSequence, barrier);
                    }
                    else if (timeDelta > spinTimeoutNanos)
                    {
                        Thread.yield();
                    }
                }
                counter = SPIN_TRIES;
            }
        }
        while (true);
    }

    @Override
    public void signalAllWhenBlocking()
    {
        fallbackStrategy.signalAllWhenBlocking();
    }
}
