// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ContextAwareRunnable;
import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ExecutorService, which limits the number of tasks running simultaneously.
 * The number of submitted tasks is unrestricted.
 *
 * @see AppExecutorUtil#createBoundedApplicationPoolExecutor(String, Executor, int) instead
 */
public final class BoundedTaskExecutor extends AbstractExecutorService {
  private volatile boolean myShutdown;
  private final @NotNull String myName;
  private final Executor myBackendExecutor;
  private final int myMaxThreads;
  // low 32 bits: number of tasks running (or trying to run)
  // high 32 bits: myTaskQueue modification stamp
  private final AtomicLong myStatus = new AtomicLong();
  private final BlockingQueue<Runnable> myTaskQueue;

  private final boolean myChangeThreadName;

  BoundedTaskExecutor(@NotNull @NonNls String name, @NotNull Executor backendExecutor, int maxThreads, boolean changeThreadName) {
    this(name, backendExecutor, maxThreads, changeThreadName, new LinkedBlockingQueue<>());
  }

  BoundedTaskExecutor(@NotNull @NonNls String name,
                      @NotNull Executor backendExecutor,
                      int maxThreads,
                      boolean changeThreadName,
                      @NotNull BlockingQueue<Runnable> queue) {
    if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) {
      Logger.getInstance(getClass()).warn("Pool name must be capitalized but got: '" + name + "'", new IllegalArgumentException());
    }
    myName = name;
    myBackendExecutor = backendExecutor;
    if (maxThreads < 1) {
      throw new IllegalArgumentException("maxThreads must be >=1 but got: " + maxThreads);
    }
    if (backendExecutor instanceof BoundedTaskExecutor) {
      throw new IllegalArgumentException("backendExecutor is already BoundedTaskExecutor: " + backendExecutor);
    }
    myMaxThreads = maxThreads;
    myChangeThreadName = changeThreadName;
    myTaskQueue = queue;
  }

  private static int getTasksInProgress(long status) {
    return (int)status;
  }

  // for diagnostics
  @VisibleForTesting
  @ApiStatus.Internal
  public static Object info(Runnable info) {
    Object task = info;
    String extra = null;
    if (task instanceof FutureTask) {
      extra = ((FutureTask<?>)task).isCancelled() ? " (future cancelled)" : ((FutureTask<?>)task).isDone() ? " (future done)" : null;
      Object t1 = ReflectionUtil.getField(task.getClass(), task, Callable.class, "callable");
      if (t1 != null) {
        task = t1;
      }
    }
    if (task instanceof Callable && task.getClass().getName().equals("java.util.concurrent.Executors$RunnableAdapter")) {
      Object t1 = ReflectionUtil.getField(task.getClass(), task, Runnable.class, "task");
      if (t1 != null) {
        task = t1;
      }
    }
    return extra == null ? task : task.getClass() + extra;
  }

  @Override
  public void shutdown() {
    myShutdown = true;
  }

  @Override
  public @NotNull List<Runnable> shutdownNow() {
    shutdown();
    return clearAndCancelAll();
  }

  @Override
  public boolean isShutdown() {
    return myShutdown;
  }

  @Override
  public boolean isTerminated() {
    return myShutdown && isEmpty() && myTaskQueue.isEmpty();
  }

  // can be executed even after shutdown
  static final class LastTask extends FutureTask<Void> {
    LastTask(@NotNull Runnable runnable) {
      super(runnable, null);
    }
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    if (!isShutdown()) {
      throw new IllegalStateException("must await termination after shutdown() or shutdownNow() only");
    }
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (!isTerminated()) {
      try {
        waitAllTasksExecuted(deadline-System.nanoTime(), TimeUnit.NANOSECONDS);
      }
      catch (ExecutionException e) {
        throw new RuntimeException(e.getCause());
      }
      catch (TimeoutException e) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected <T> @NotNull RunnableFuture<T> newTaskFor(@NotNull Runnable runnable, T value) {
    return newTaskFor(Executors.callable(runnable, value));
  }

  @Override
  protected <T> @NotNull RunnableFuture<T> newTaskFor(@NotNull Callable<T> callable) {
    return AppScheduledExecutorService.capturePropagationAndCancellationContext(callable);
  }

  @Override
  public void execute(@NotNull Runnable command) {
    Runnable task = command instanceof LastTask ? command : AppScheduledExecutorService.capturePropagationAndCancellationContext(command);
    if (isShutdown() && !(task instanceof LastTask)) {
      throw new RejectedExecutionException(this+" is already shutdown, trying to execute "+task+" ("+task.getClass()+")");
    }
    long status = incrementCounterAndTimestamp(); // increment inProgress and queue-stamp atomically

    int inProgress = getTasksInProgress(status);

    assert inProgress > 0 : inProgress;
    if (inProgress <= myMaxThreads) {
      // optimisation: can execute without queue/dequeue
      wrapAndExecute(task, status);
      return;
    }

    if (!myTaskQueue.offer(task)) {
      throw new RejectedExecutionException();
    }
    Runnable next = pollOrGiveUp(status);
    if (next != null) {
      wrapAndExecute(next, status);
    }
  }

  private long incrementCounterAndTimestamp() {
    // avoid "task number" bits to be garbled on overflow
    return myStatus.updateAndGet(status -> status + 1 + (1L << 32) & 0x7fffffffffffffffL);
  }

  // return next task taken from the queue if it can be executed now
  // or atomically decrement the task counter and return null
  private Runnable pollOrGiveUp(long status) {
    while (true) {
      int inProgress = getTasksInProgress(status);
      assert inProgress > 0 : inProgress;

      Runnable next;
      if (inProgress <= myMaxThreads && (next = myTaskQueue.poll()) != null) {
        return next;
      }
      if (myStatus.compareAndSet(status, status - 1)) {
        break;
      }
      status = myStatus.get();
    }
    return null;
  }

  private void wrapAndExecute(@NotNull Runnable firstTask, long status) {
    try {
      Runnable command = new ContextAwareRunnable() {
        final AtomicReference<Runnable> currentTask = new AtomicReference<>(firstTask);
        @Override
        public void run() {
          AccessToken token = AppExecutorUtil.propagateContext() ? ThreadContext.resetThreadContext() : AccessToken.EMPTY_ACCESS_TOKEN;
          //noinspection TryFinallyCanBeTryWithResources
          try {
            // This runnable is intended to be used for offloading the queue by executing stored tasks.
            // It means that it shall not possess a thread context,
            // but the executed tasks must have a context.
            if (myChangeThreadName) {
              ConcurrencyUtil.runUnderThreadName(myName, this::executeFirstTaskAndHelpQueue);
            }
            else {
              executeFirstTaskAndHelpQueue();
            }
          }
          finally {
            token.close();
          }
        }

        private void executeFirstTaskAndHelpQueue() {
          Runnable task = currentTask.get();
          do {
            currentTask.set(task);
            doRun(task);
            task = pollOrGiveUp(status);
          }
          while (task != null);
        }

        @Override
        public String toString() {
          return String.valueOf(info(currentTask.get()));
        }
      };
      myBackendExecutor.execute(command);
    }
    catch (Error | RuntimeException e) {
      myStatus.decrementAndGet();
      throw e;
    }
  }

  // Extracted to have a capture point
  private static void doRun(@Async.Execute @NotNull Runnable task) {
    try {
      task.run();
    }
    catch (Throwable e) {
      // do not lose queued tasks because of this exception
      if (!(e instanceof ControlFlowException)) {
        try {
          Logger.getInstance(BoundedTaskExecutor.class).error(e);
        }
        catch (Throwable ignored) {
        }
      }
    }
  }

  /**
   * Wait for this executor for all queued tasks to finish executing by {@link #myBackendExecutor} in their respective threads.
   */
  @ApiStatus.Internal
  public synchronized void waitAllTasksExecuted(long timeout, @NotNull TimeUnit unit)
    throws ExecutionException, InterruptedException, TimeoutException {
    CountDownLatch started = new CountDownLatch(myMaxThreads);
    CountDownLatch readyToFinish = new CountDownLatch(1);
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          started.countDown();
          readyToFinish.await();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }

      @Override
      public String toString() {
        return "LastTask to waitAllTasksExecuted for " + timeout + " " + unit + " (" + System.identityHashCode(this) + ")";
      }
    };
    // Submit 'myMaxTasks' runnables and wait for them all to start.
    // They will spread to all executor threads and ensure the previously submitted tasks are completed.
    // Wait for all empty runnables to finish freeing up the threads.
    List<Future<?>> futures = ContainerUtil.map(Collections.nCopies(myMaxThreads, null), __ -> {
      LastTask wait = new LastTask(runnable);
      execute(wait);
      return wait;
    });
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    try {
      if (!started.await(timeout, unit)) {
        throw new TimeoutException("Interrupted by timeout. " + this);
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    finally {
      readyToFinish.countDown();
    }
    ConcurrencyUtil.getAll(Math.max(0, deadline-System.nanoTime()), TimeUnit.NANOSECONDS, futures);
  }

  public boolean isEmpty() {
    return getTasksInProgress(myStatus.get()) == 0;
  }

  public @NotNull List<Runnable> clearAndCancelAll() {
    List<Runnable> queued = new ArrayList<>(myTaskQueue.size());
    myTaskQueue.drainTo(queued);
    for (Runnable fromQueue : queued) {
      Runnable task = Propagation.unwrapContextRunnable(fromQueue);
      if (task instanceof FutureTask && !(task instanceof LastTask)) {
        ((FutureTask<?>)task).cancel(false);
      }
    }
    return queued;
  }

  @Override
  public String toString() {
    int size = myTaskQueue.size();
    return "BoundedExecutor(" + myMaxThreads + ")" + (isShutdown() ? " SHUTDOWN " : "") +
           "; inProgress: " + getTasksInProgress(myStatus.get()) + (size == 0 ? "" : "; queue: " + size) +
           "; name: " + myName;
  }
}
