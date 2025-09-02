// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ContextAwareRunnable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.util.concurrency.AppExecutorUtil.propagateContext;

/**
 * Makes a {@link ScheduledExecutorService} from the supplied plain non-scheduling {@link ExecutorService} by awaiting scheduled tasks in a separate thread
 * and then passing them for execution to the {@code backendExecutorService}.
 * Unlike the standard {@link ScheduledThreadPoolExecutor}, this pool can be unbounded if the {@code backendExecutorService} is.
 * Used for reducing the number of always-running threads, because it reuses the threads from the supplied pool.
 */
@ApiStatus.Internal
public class SchedulingWrapper implements ScheduledExecutorService {
  private static final Logger LOG = Logger.getInstance(SchedulingWrapper.class);
  
  private final AtomicBoolean shutdown = new AtomicBoolean();
  @VisibleForTesting
  public final @NotNull ExecutorService backendExecutorService;
  protected final AppDelayQueue delayQueue;

  // make sure transferrerThread doesn't retain a task on its stack
  private final MyScheduledFutureTask<Void> myLaxativePill = new MyScheduledFutureTask<Void>(()->{}, null, 0) {
    @Override
    public boolean executeMeInBackendExecutor() {
      // only after transferrerThread handled this (and all previous) task we can call backendExecutorService.shutdown()
      onDelayQueuePurgedOnShutdown();
      set(null);
      return true;
    }

    @Override
    public String toString() {
      return "laxative for "+SchedulingWrapper.this;
    }
  };

  public SchedulingWrapper(@NotNull ExecutorService backendExecutorService, @NotNull AppDelayQueue delayQueue) {
    this.delayQueue = delayQueue;
    if (backendExecutorService instanceof ScheduledExecutorService) {
      throw new IllegalArgumentException("backendExecutorService: "+backendExecutorService+" is already ScheduledExecutorService");
    }
    this.backendExecutorService = backendExecutorService;
  }

  public @NotNull AppDelayQueue getDelayQueue() {
    return delayQueue;
  }
  
  @Override
  public @Unmodifiable @NotNull List<Runnable> shutdownNow() {
    List<Runnable> canceled = doShutdown();
    List<Runnable> backEndCanceled = backendExecutorService instanceof BoundedTaskExecutor
                                     ? ((BoundedTaskExecutor)backendExecutorService).clearAndCancelAll()
                                     : Collections.emptyList();
    try {
      myLaxativePill.get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    return ContainerUtil.concat(canceled, backEndCanceled);
  }

  @Override
  public void shutdown() {
    doShutdown();
  }

  @NotNull
  List<Runnable> doShutdown() {
    if (!shutdown.compareAndSet(false, true)) {
      return Collections.emptyList();
    }

    List<MyScheduledFutureTask<?>> toCancel = getMyTasksFromDelayQueue();
    for (MyScheduledFutureTask<?> task : toCancel) {
      task.cancel(false);
    }
    delayQueue.removeAll(toCancel);

    delayQueue.offer(myLaxativePill);
    //noinspection unchecked,rawtypes
    return (List)toCancel;
  }

  void onDelayQueuePurgedOnShutdown() {
  }

  private @NotNull List<MyScheduledFutureTask<?>> getMyTasksFromDelayQueue() {
    List<MyScheduledFutureTask<?>> result = new ArrayList<>();
    for (MyScheduledFutureTask<?> task : delayQueue) {
      if (task.getBackendExecutorService() == backendExecutorService) {
        result.add(task);
      }
    }
    return result;
  }

  @Override
  public boolean isShutdown() {
    return shutdown.get();
  }

  @Override
  public boolean isTerminated() {
    return isShutdown() && backendExecutorService.isTerminated() && getMyTasksFromDelayQueue().isEmpty();
  }
  @TestOnly
  public void assertTerminated() {
    assert isShutdown();
    assert backendExecutorService.isTerminated() : backendExecutorService;
    List<MyScheduledFutureTask<?>> tasks = getMyTasksFromDelayQueue();
    assert tasks.isEmpty() : tasks;
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    if (!isShutdown()) {
      throw new IllegalStateException("must await termination after shutdown() or shutdownNow() only");
    }
    // the purpose of this complicated machinery below is to guarantee no task crash with "pool already shutdown" during transfer to getBackendBoundedExecutor() in AppDelayQueue.scheduledToPooledTransferrer loop
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    try {
      // wait for backendExecutorService.shutdown() (in onDelayQueuePurgedOnShutdown()) to be able to call backendExecutorService.awaitTermination()
      myLaxativePill.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
    }
    catch (TimeoutException e) {
      return false;
    }
    catch (ExecutionException | CancellationException ignored) {
    }

    List<MyScheduledFutureTask<?>> tasks = getMyTasksFromDelayQueue();
    for (MyScheduledFutureTask<?> task : tasks) {
      try {
        task.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
      }
      catch (ExecutionException | CancellationException ignored) {
      }
      catch (TimeoutException e) {
        return false;
      }
    }
    delayQueue.removeAll(tasks);

    return backendExecutorService.awaitTermination(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
  }

  @ApiStatus.Internal
  public class MyScheduledFutureTask<V> extends FutureTask<V> implements RunnableScheduledFuture<V>, ContextAwareRunnable {
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
    public MyScheduledFutureTask(@NotNull Runnable r, V result, long ns) {
      super(r, result);
      time = ns;
      period = 0;
      sequenceNumber = sequencer.getAndIncrement();
    }

    /**
     * Creates a periodic action with given nano time and period.
     */
    MyScheduledFutureTask(@NotNull Runnable r, V result, long ns, long period) {
      super(r, result);
      time = ns;
      this.period = period;
      sequenceNumber = sequencer.getAndIncrement();
    }

    /**
     * Creates a one-shot action with given nanoTime-based trigger time.
     */
    MyScheduledFutureTask(@NotNull Callable<V> callable, long ns) {
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
      return unit.convert(time - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(@NotNull Delayed other) {
      if (other == this) {
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
        time = triggerTime(-p);
      }
    }

    /**
     * Overrides FutureTask version so as to reset/requeue if periodic.
     */
    @Override
    public void run() {
      if (!isPeriodic()) {
        super.run();
        futureDone(this);
      }
      else if (runAndReset() && !isShutdown()) {
        setNextRunTime();
        delayQueue.offer(this);
        if (isShutdown()) {
          delayQueue.remove(this);
        }
      }
    }

    @Override
    protected void setException(Throwable t) {
      try {
        if (!Logger.shouldRethrow(t)) {
          LOG.error(t);
        }
      }
      finally {
        super.setException(t);
      }
    }

    @Override
    public String toString() {
      Object info = BoundedTaskExecutor.info(this);
      return "Delay: " + getDelay(TimeUnit.MILLISECONDS) + "ms; " + (info == this ? super.toString() : info) + " backendExecutorService: "+backendExecutorService;
    }

    private @NotNull ExecutorService getBackendExecutorService() {
      return backendExecutorService;
    }

    // return false if this is the last task in the queue
    public boolean executeMeInBackendExecutor() {
      // optimization: can be canceled already
      if (!isDone()) {
        backendExecutorService.execute(this);
      }
      return true;
    }
  }

  protected void futureDone(@NotNull Future<?> task) {
  }

  /**
   * Sequence number to break scheduling ties, and in turn to
   * guarantee FIFO order among tied entries.
   */
  private static final AtomicLong sequencer = new AtomicLong();

  /**
   * Returns the trigger time of a delayed action.
   */
  @ApiStatus.Internal
  public long triggerTime(long delay, @NotNull TimeUnit unit) {
    return triggerTime(unit.toNanos(delay < 0 ? 0 : delay));
  }

  /**
   * Returns the trigger time of a delayed action.
   */
  private long triggerTime(long delay) {
    return System.nanoTime() + (delay < Long.MAX_VALUE >> 1 ? delay : overflowFree(delay));
  }

  /**
   * Constrains the values of all delays in the queue to be within
   * Long.MAX_VALUE of each other, to avoid overflow in compareTo.
   * This may occur if a task is eligible to be dequeued, but has
   * not yet been, while some other task is added with a delay of
   * Long.MAX_VALUE.
   */
  private long overflowFree(long delay) {
    Delayed head = delayQueue.peek();
    if (head != null) {
      long headDelay = head.getDelay(TimeUnit.NANOSECONDS);
      if (headDelay < 0 && delay - headDelay < 0) {
        delay = Long.MAX_VALUE + headDelay;
      }
    }
    return delay;
  }

  @Override
  public @NotNull ScheduledFuture<?> schedule(@NotNull Runnable command, long delay, @NotNull TimeUnit unit) {
    return schedule(Executors.callable(command), delay, unit);
  }

  @Override
  public @NotNull <V> ScheduledFuture<V> schedule(@NotNull Callable<V> callable, long delay, @NotNull TimeUnit unit) {
    return delayedExecute(createTask(callable, triggerTime(delay, unit)));
  }

  private <V> @NotNull MyScheduledFutureTask<V> createTask(@NotNull Callable<V> callable, long ns) {
    if (!propagateContext()) {
      return new MyScheduledFutureTask<>(callable, ns);
    }
    return Propagation.capturePropagationContext(this, callable, ns);
  }

  @NotNull
  public <T> MyScheduledFutureTask<T> delayedExecute(@NotNull MyScheduledFutureTask<T> t) {
    checkAlreadyShutdown();
    delayQueue.offer(t);
    if (t.getDelay(TimeUnit.DAYS) > 31 && !t.isPeriodic()) {
      // guard against inadvertent queue overflow
      throw new IllegalArgumentException("Unsupported crazy delay " + t.getDelay(TimeUnit.DAYS) + " days: " + BoundedTaskExecutor.info(t));
    }
    return t;
  }

  private void checkAlreadyShutdown() {
    if (isShutdown()) {
      throw new RejectedExecutionException("Already shutdown");
    }
  }

  @Override
  public @NotNull ScheduledFuture<?> scheduleAtFixedRate(@NotNull Runnable command, long initialDelay, long period, @NotNull TimeUnit unit) {
    throw new IncorrectOperationException("Not supported because it's bad for hibernation; use scheduleWithFixedDelay() with the same parameters instead.");
  }

  @Override
  public @NotNull ScheduledFuture<?> scheduleWithFixedDelay(@NotNull Runnable command, long initialDelay, long delay, @NotNull TimeUnit unit) {
    if (delay <= 0) {
      throw new IllegalArgumentException("delay must be positive but got: " + delay);
    }
    return delayedExecute(createTask(
      command,
      triggerTime(initialDelay, unit),
      unit.toNanos(-delay)
    ));
  }

  private @NotNull MyScheduledFutureTask<?> createTask(@NotNull Runnable command, long ns, long period) {
    if (!propagateContext()) {
      return new MyScheduledFutureTask<>(command, null, ns, period);
    }
    return Propagation.capturePropagationContext(this, command, ns, period);
  }

  /////////////////////// delegates for ExecutorService ///////////////////////////

  @Override
  public @NotNull <T> Future<T> submit(@NotNull Callable<T> task) {
    checkAlreadyShutdown();
    return backendExecutorService.submit(task);
  }

  @Override
  public @NotNull <T> Future<T> submit(@NotNull Runnable task, T result) {
    checkAlreadyShutdown();
    return backendExecutorService.submit(task, result);
  }

  @Override
  public @NotNull Future<?> submit(@NotNull Runnable task) {
    checkAlreadyShutdown();
    return backendExecutorService.submit(task);
  }

  @Override
  public @NotNull <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
    checkAlreadyShutdown();
    return backendExecutorService.invokeAll(tasks);
  }

  @Override
  public @NotNull <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    checkAlreadyShutdown();
    return backendExecutorService.invokeAll(tasks, timeout, unit);
  }

  @Override
  public @NotNull <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    checkAlreadyShutdown();
    return backendExecutorService.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks, long timeout, @NotNull TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException {
    checkAlreadyShutdown();
    return backendExecutorService.invokeAny(tasks, timeout, unit);
  }

  @Override
  public void execute(@NotNull Runnable command) {
    checkAlreadyShutdown();
    backendExecutorService.execute(command);
  }
}
