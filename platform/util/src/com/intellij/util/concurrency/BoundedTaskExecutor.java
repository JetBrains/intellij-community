// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.util.concurrency.AppScheduledExecutorService.handleCommand;
import static com.intellij.util.concurrency.AppScheduledExecutorService.handleTask;

/**
 * ExecutorService which limits the number of tasks running simultaneously.
 * The number of submitted tasks is unrestricted.
 *
 * @see AppExecutorUtil#createBoundedApplicationPoolExecutor(String, Executor, int) instead
 */
public final class BoundedTaskExecutor extends AbstractExecutorService {

  private volatile boolean myShutdown;
  @NotNull
  private final String myName;
  private final Executor myBackendExecutor;
  private final int myMaxThreads;
  // low  32 bits: number of tasks running (or trying to run)
  // high 32 bits: myTaskQueue modification stamp
  private final AtomicLong myStatus = new AtomicLong();
  private final BlockingQueue<Runnable> myTaskQueue;

  private final boolean myChangeThreadName;

  BoundedTaskExecutor(@NotNull @NonNls String name, @NotNull Executor backendExecutor, int maxThreads, boolean changeThreadName) {
    this(name, backendExecutor, maxThreads, changeThreadName, new LinkedBlockingQueue<>());
    if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) {
      Logger.getInstance(getClass()).warn("Pool name must be capitalized but got: '" + name + "'", new IllegalArgumentException());
    }
  }

  BoundedTaskExecutor(@NotNull @NonNls String name,
                      @NotNull Executor backendExecutor,
                      int maxThreads,
                      boolean changeThreadName,
                      @NotNull BlockingQueue<Runnable> queue) {
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

  // for diagnostics
  static Object info(Runnable info) {
    Object task = info;
    String extra = null;
    if (task instanceof FutureTask) {
      extra = ((FutureTask<?>)task).isCancelled() ? " (future cancelled)" : ((FutureTask<?>)task).isDone() ? " (future done)" : null;
      task = ObjectUtils.chooseNotNull(ReflectionUtil.getField(task.getClass(), task, Callable.class, "callable"), task);
    }
    if (task instanceof Callable && task.getClass().getName().equals("java.util.concurrent.Executors$RunnableAdapter")) {
      task = ObjectUtils.chooseNotNull(ReflectionUtil.getField(task.getClass(), task, Runnable.class, "task"), task);
    }
    return extra == null ? task : task.getClass() + extra;
  }

  @Override
  public void shutdown() {
    if (myShutdown) throw new IllegalStateException("Already shut down: " + this);
    myShutdown = true;
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    shutdown();
    return clearAndCancelAll();
  }

  @Override
  public boolean isShutdown() {
    return myShutdown;
  }

  @Override
  public boolean isTerminated() {
    return myShutdown;
  }

  // can be executed even after shutdown
  private static class LastTask extends FutureTask<Void> {
    LastTask(@NotNull Runnable runnable) {
      super(runnable, null);
    }
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    if (!isShutdown()) throw new IllegalStateException("you must call shutdown() or shutdownNow() first");
    try {
      waitAllTasksExecuted(timeout, unit);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
    catch (TimeoutException e) {
      return false;
    }
    return true;
  }

  @Override
  protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
    return newTaskFor(Executors.callable(runnable, value));
  }

  @Override
  protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
    return handleTask(callable);
  }

  @Override
  public void execute(@NotNull Runnable command) {
    executeRaw(handleCommand(command));
  }

  private void executeRaw(@NotNull Runnable task) {
    if (isShutdown() && !(task instanceof LastTask)) {
      throw new RejectedExecutionException("Already shutdown");
    }
    long status = incrementCounterAndTimestamp(); // increment inProgress and queue-stamp atomically

    int inProgress = (int)status;

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
    // avoid "tasks number" bits to be garbled on overflow
    return myStatus.updateAndGet(status -> status + 1 + (1L << 32) & 0x7fffffffffffffffL);
  }

  // return next task taken from the queue if it can be executed now
  // or decrement my counter (atomically) and return null
  private Runnable pollOrGiveUp(long status) {
    while (true) {
      int inProgress = (int)status;
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
      AtomicReference<Runnable> currentTask = new AtomicReference<>(firstTask);
      Runnable command = new Runnable() {
        @Override
        public void run() {
          if (myChangeThreadName) {
            String name = myName;
            if (StartUpMeasurer.isEnabled()) {
              name += "[" + Thread.currentThread().getName() + "]";
            }
            ConcurrencyUtil.runUnderThreadName(name, this::execute);
          }
          else {
            execute();
          }
        }

        private void execute() {
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
      if (myBackendExecutor instanceof ContextPropagatingExecutor) {
        ((ContextPropagatingExecutor)myBackendExecutor).executeRaw(command);
      }
      else {
        myBackendExecutor.execute(command);
      }
    }
    catch (Error | RuntimeException e) {
      myStatus.decrementAndGet();
      throw e;
    }
  }

  // Extracted to have a capture point
  private static void doRun(@Async.Execute Runnable task) {
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
    for (Future<?> future : futures) {
      future.get(timeout, unit);
    }
  }

  public boolean isEmpty() {
    return (int)myStatus.get() == 0;
  }

  public int getQueueSize() {
    return myTaskQueue.size();
  }

  @NotNull
  public List<Runnable> clearAndCancelAll() {
    List<Runnable> queued = new ArrayList<>();
    myTaskQueue.drainTo(queued);
    for (Runnable task : queued) {
      if (task instanceof FutureTask) {
        ((FutureTask<?>)task).cancel(false);
      }
    }
    return queued;
  }

  @Override
  public String toString() {
    return "BoundedExecutor(" + myMaxThreads + ")" + (isShutdown() ? " SHUTDOWN " : "") +
           "; inProgress: " + (int)myStatus.get() +
           (
             myTaskQueue.isEmpty()
             ? ""
             : "; queue: " + myTaskQueue.size() + "[" + ContainerUtil.map(myTaskQueue, BoundedTaskExecutor::info) + "]"
           ) +
           "; name: " + myName;
  }
}
