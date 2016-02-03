/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Makes a {@link ScheduledExecutorService} from the supplied plain, non-scheduling {@link ExecutorService} by awaiting scheduled tasks in a separate thread
 * and then passing them for execution to the {@code backendExecutorService}.
 * Unlike the existing {@link ScheduledThreadPoolExecutor}, this pool can be unbounded if the {@code backendExecutorService} is.
 */
class SchedulingWrapper implements ScheduledExecutorService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.concurrency.SchedulingWrapper");
  private final AtomicBoolean shutdown = new AtomicBoolean();
  @NotNull final ExecutorService backendExecutorService;
  final AppDelayQueue delayQueue;

  SchedulingWrapper(@NotNull final ExecutorService backendExecutorService, @NotNull AppDelayQueue delayQueue) {
    this.delayQueue = delayQueue;
    if (backendExecutorService instanceof ScheduledExecutorService) {
      throw new IllegalArgumentException("backendExecutorService: "+backendExecutorService+" is already ScheduledExecutorService");
    }
    this.backendExecutorService = backendExecutorService;
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    return doShutdownNow();
  }

  @Override
  public void shutdown() {
    doShutdown();
  }

  void doShutdown() {
    if (!shutdown.compareAndSet(false, true)) {
      throw new IllegalStateException("Already shutdown");
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Shutdown", new Throwable());
    }
  }

  @NotNull
  List<Runnable> doShutdownNow() {
    doShutdown(); // shutdown me first to avoid further delayQueue offers
    List<MyScheduledFutureTask> result = ContainerUtil.filter(delayQueue, new Condition<MyScheduledFutureTask>() {
      @Override
      public boolean value(MyScheduledFutureTask task) {
        if (task.getBackendExecutorService() == backendExecutorService) {
          task.cancel(false);
          return true;
        }
        return false;
      }
    });
    delayQueue.removeAll(new HashSet<MyScheduledFutureTask>(result));
    if (LOG.isTraceEnabled()) {
      LOG.trace("Shutdown. Drained tasks: "+result);
    }
    //noinspection unchecked
    return (List)result;
  }

  @Override
  public boolean isShutdown() {
    return shutdown.get();
  }

  @Override
  public boolean isTerminated() {
    return isShutdown();
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    return isTerminated();
  }

  class MyScheduledFutureTask<V> extends FutureTask<V> implements RunnableScheduledFuture<V> {
    /**
     * Sequence number to break ties FIFO
     */
    private final long sequenceNumber;

    /**
     * The time the task is enabled to execute in nanoTime units
     */
    private long time;

    /**
     * Period in nanoseconds for repeating tasks.  A positive
     * value indicates fixed-rate execution.  A negative value
     * indicates fixed-delay execution.  A value of 0 indicates a
     * non-repeating task.
     */
    private final long period;

    /**
     * Creates a one-shot action with given nanoTime-based trigger time.
     */
    private MyScheduledFutureTask(@NotNull Runnable r, V result, long ns) {
      super(r, result);
      time = ns;
      period = 0;
      sequenceNumber = sequencer.getAndIncrement();
    }

    /**
     * Creates a periodic action with given nano time and period.
     */
    private MyScheduledFutureTask(@NotNull Runnable r, V result, long ns, long period) {
      super(r, result);
      time = ns;
      this.period = period;
      sequenceNumber = sequencer.getAndIncrement();
    }

    /**
     * Creates a one-shot action with given nanoTime-based trigger time.
     */
    private MyScheduledFutureTask(@NotNull Callable<V> callable, long ns) {
      super(callable);
      time = ns;
      period = 0;
      sequenceNumber = sequencer.getAndIncrement();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      boolean canceled = super.cancel(mayInterruptIfRunning);
      delayQueue.remove(this);
      return canceled;
    }

    @Override
    public long getDelay(@NotNull TimeUnit unit) {
      return unit.convert(time - now(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(@NotNull Delayed other) {
      if (other == this) // compare zero if same object
      {
        return 0;
      }
      if (other instanceof MyScheduledFutureTask) {
        MyScheduledFutureTask<?> x = (MyScheduledFutureTask<?>)other;
        long diff = time - x.time;
        if (diff < 0) {
          return -1;
        }
        if (diff > 0) {
          return 1;
        }
        if (sequenceNumber < x.sequenceNumber) {
          return -1;
        }
        return 1;
      }
      long diff = getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
      return diff < 0 ? -1 : diff > 0 ? 1 : 0;
    }

    /**
     * Returns {@code true} if this is a periodic (not a one-shot) action.
     *
     * @return {@code true} if periodic
     */
    @Override
    public boolean isPeriodic() {
      return period != 0;
    }

    /**
     * Sets the next time to run for a periodic task.
     */
    private void setNextRunTime() {
      long p = period;
      if (p > 0) {
        time += p;
      }
      else {
        time = triggerTime(delayQueue, -p);
      }
    }

    /**
     * Overrides FutureTask version so as to reset/requeue if periodic.
     */
    @Override
    public void run() {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Executing " + BoundedTaskExecutor.info(this));
      }
      boolean periodic = isPeriodic();
      if (backendExecutorService.isShutdown()) {
        cancel(false);
      }
      else if (!periodic) {
        super.run();
      }
      else if (runAndReset()) {
        setNextRunTime();
        delayQueue.offer(this);
      }
    }

    @Override
    public String toString() {
      return "Delay: " + getDelay(TimeUnit.MILLISECONDS) + "ms; " + BoundedTaskExecutor.info(this);
    }

    @NotNull
    ExecutorService getBackendExecutorService() {
      return backendExecutorService;
    }
  }

  /**
   * Sequence number to break scheduling ties, and in turn to
   * guarantee FIFO order among tied entries.
   */
  private static final AtomicLong sequencer = new AtomicLong();

  /**
   * Returns the trigger time of a delayed action.
   */
  private static long triggerTime(@NotNull AppDelayQueue queue, long delay, TimeUnit unit) {
    return triggerTime(queue, unit.toNanos(delay < 0 ? 0 : delay));
  }

  private static long now() {
    return System.nanoTime();
  }

  /**
   * Returns the trigger time of a delayed action.
   */
  private static long triggerTime(@NotNull AppDelayQueue queue, long delay) {
    return now() + (delay < Long.MAX_VALUE >> 1 ? delay : overflowFree(queue, delay));
  }

  /**
   * Constrains the values of all delays in the queue to be within
   * Long.MAX_VALUE of each other, to avoid overflow in compareTo.
   * This may occur if a task is eligible to be dequeued, but has
   * not yet been, while some other task is added with a delay of
   * Long.MAX_VALUE.
   */
  private static long overflowFree(@NotNull AppDelayQueue queue, long delay) {
    Delayed head = queue.peek();
    if (head != null) {
      long headDelay = head.getDelay(TimeUnit.NANOSECONDS);
      if (headDelay < 0 && delay - headDelay < 0) {
        delay = Long.MAX_VALUE + headDelay;
      }
    }
    return delay;
  }

  @NotNull
  @Override
  public ScheduledFuture<?> schedule(@NotNull Runnable command,
                                     long delay,
                                     @NotNull TimeUnit unit) {
    MyScheduledFutureTask<?> t = new MyScheduledFutureTask<Void>(command, null, triggerTime(delayQueue, delay, unit));
    return delayedExecute(t);
  }

  @NotNull
  private <T> MyScheduledFutureTask<T> delayedExecute(@NotNull MyScheduledFutureTask<T> t) {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Submit at delay " + t.getDelay(TimeUnit.MILLISECONDS) + "ms " + BoundedTaskExecutor.info(t));
    }
    if (isShutdown()) {
      throw new RejectedExecutionException("Already shutdown");
    }
    delayQueue.add(t);
    return t;
  }

  @NotNull
  @Override
  public <V> ScheduledFuture<V> schedule(@NotNull Callable<V> callable,
                                         long delay,
                                         @NotNull TimeUnit unit) {
    MyScheduledFutureTask<V> t = new MyScheduledFutureTask<V>(callable, triggerTime(delayQueue, delay, unit));
    return delayedExecute(t);
  }

  @NotNull
  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(@NotNull Runnable command,
                                                long initialDelay,
                                                long period,
                                                @NotNull TimeUnit unit) {
    throw new IncorrectOperationException("Not supported because it's bad for hibernation; use scheduleWithFixedDelay() instead.");
  }

  @NotNull
  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(@NotNull Runnable command,
                                                   long initialDelay,
                                                   long delay,
                                                   @NotNull TimeUnit unit) {
    if (delay <= 0) {
      throw new IllegalArgumentException("delay must be positive but got: "+delay);
    }
    MyScheduledFutureTask<Void> sft = new MyScheduledFutureTask<Void>(command,
                                                                      null,
                                                                      triggerTime(delayQueue, initialDelay, unit),
                                                                      unit.toNanos(-delay));
    return delayedExecute(sft);
  }

  /////////////////////// delegates for ExecutorService ///////////////////////////

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Callable<T> task) {
    return backendExecutorService.submit(task);
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Runnable task, T result) {
    return backendExecutorService.submit(task, result);
  }

  @NotNull
  @Override
  public Future<?> submit(@NotNull Runnable task) {
    return backendExecutorService.submit(task);
  }

  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return backendExecutorService.invokeAll(tasks);
  }

  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    return backendExecutorService.invokeAll(tasks, timeout, unit);
  }

  @NotNull
  @Override
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return backendExecutorService.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException {
    return backendExecutorService.invokeAny(tasks, timeout, unit);
  }

  @Override
  public void execute(@NotNull Runnable command) {
    backendExecutorService.execute(command);
  }
}
