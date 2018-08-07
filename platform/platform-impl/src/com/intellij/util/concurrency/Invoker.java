// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.util.containers.TransferToEDTQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Obsolescent;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.util.Disposer.register;
import static java.awt.EventQueue.isDispatchThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Sergey.Malenkov
 */
public abstract class Invoker implements Disposable {
  private static final int THRESHOLD = Integer.MAX_VALUE;
  private static final Logger LOG = Logger.getInstance(Invoker.class);
  private static final AtomicInteger UID = new AtomicInteger();
  private final AtomicInteger count = new AtomicInteger();
  private final String description;
  private volatile boolean disposed;

  private Invoker(@NotNull String prefix, @NotNull Disposable parent) {
    description = UID.getAndIncrement() + ".Invoker." + prefix + ":" + parent.getClass().getName();
    register(parent, this);
  }

  @Override
  public String toString() {
    return description;
  }

  @Override
  public void dispose() {
    disposed = true;
  }

  /**
   * Returns {@code true} if the current thread allows to process a task.
   *
   * @return {@code true} if the current thread is valid, or {@code false} otherwise
   */
  public abstract boolean isValidThread();

  /**
   * Invokes the specified task asynchronously on the valid thread.
   * Even if this method is called from the valid thread
   * the specified task will still be deferred
   * until all pending events have been processed.
   *
   * @param task a task to execute asynchronously on the valid thread
   * @return an object to control task processing
   */
  @NotNull
  public final CancellablePromise<?> invokeLater(@NotNull Runnable task) {
    return invokeLater(task, 0);
  }

  /**
   * Invokes the specified task on the valid thread after the specified delay.
   *
   * @param task  a task to execute asynchronously on the valid thread
   * @param delay milliseconds for the initial delay
   * @return an object to control task processing
   */
  @NotNull
  public final CancellablePromise<?> invokeLater(@NotNull Runnable task, int delay) {
    if (delay < 0) throw new IllegalArgumentException("delay must be non-negative: " + delay);
    AsyncPromise<?> promise = new AsyncPromise<>();
    if (canInvoke(task, promise)) {
      count.incrementAndGet();
      offer(() -> invokeSafely(task, promise, 0), delay);
    }
    return promise;
  }

  /**
   * Invokes the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   * @return an object to control task processing
   */
  @NotNull
  public final CancellablePromise<?> runOrInvokeLater(@NotNull Runnable task) {
    if (isValidThread()) {
      count.incrementAndGet();
      AsyncPromise<?> promise = new AsyncPromise<>();
      invokeSafely(task, promise, 0);
      return promise;
    }
    return invokeLater(task);
  }

  /**
   * @deprecated use {@link #runOrInvokeLater(Runnable)}
   */
  @Deprecated
  public final void invokeLaterIfNeeded(@NotNull Runnable task) {
    runOrInvokeLater(task);
  }

  /**
   * Returns a workload of the task queue.
   *
   * @return amount of tasks, which are executing or waiting for execution
   */
  public final int getTaskCount() {
    return disposed ? 0 : count.get();
  }

  abstract void offer(@NotNull Runnable runnable, int delay);

  /**
   * @param task    a task to execute on the valid thread
   * @param promise an object to control task processing
   * @param attempt an attempt to run the specified task
   */
  private void invokeSafely(@NotNull Runnable task, @NotNull AsyncPromise<?> promise, int attempt) {
    try {
      if (canInvoke(task, promise)) {
        if (isDispatchThread() || getApplication() == null) {
          // do not care about ReadAction in EDT and in tests without application
          task.run();
        }
        else if (getApplication().isReadAccessAllowed()) {
          if (((ApplicationEx)getApplication()).isWriteActionPending()) throw new ProcessCanceledException();
          task.run();
        }
        else {
          // try to execute a task until it stops throwing ProcessCanceledException
          while (!ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(task)) {
            if (!canInvoke(task, promise)) return; // stop execution of obsolete task
            ProgressIndicatorUtils.yieldToPendingWriteActions();
            if (!canRestart(task, promise, attempt)) return;
            LOG.debug("Task is restarted");
            attempt++;
          }
        }
        promise.setResult(null);
      }
    }
    catch (ProcessCanceledException exception) {
      if (canRestart(task, promise, attempt)) {
        count.incrementAndGet();
        int nextAttempt = attempt + 1;
        offer(() -> invokeSafely(task, promise, nextAttempt), 10);
        LOG.debug("Task is restarted");
      }
    }
    catch (Exception exception) {
      LOG.warn(exception);
      promise.setError(exception);
    }
    catch (Throwable throwable) {
      LOG.warn(throwable);
      promise.setError(throwable);
      throw throwable;
    }
    finally {
      count.decrementAndGet();
    }
  }

  /**
   * @param task    a task to execute on the valid thread
   * @param promise an object to control task processing
   * @param attempt an attempt to run the specified task
   * @return {@code false} if too many attempts to run the task,
   * or if the given promise is already done or cancelled,
   * or if the current invoker is disposed,
   * or if the specified task is obsolete
   */
  private boolean canRestart(@NotNull Runnable task, @NotNull AsyncPromise<?> promise, int attempt) {
    LOG.debug("Task is canceled");
    if (attempt < THRESHOLD) return canInvoke(task, promise);
    LOG.warn("Task is always canceled: " + task);
    promise.setError("timeout");
    return false;
  }

  /**
   * @param task    a task to execute on the valid thread
   * @param promise an object to control task processing
   * @return {@code false} if the given promise is already done or cancelled,
   * or if the current invoker is disposed,
   * or if the specified task is obsolete
   */
  private boolean canInvoke(@NotNull Runnable task, @NotNull AsyncPromise<?> promise) {
    if (promise.isDone()) {
      LOG.debug("Promise is cancelled: ", promise.isCancelled());
      return false;
    }
    if (disposed) {
      LOG.debug("Invoker is disposed");
      promise.setError("disposed");
      return false;
    }
    if (task instanceof Obsolescent) {
      Obsolescent obsolescent = (Obsolescent)task;
      if (obsolescent.isObsolete()) {
        LOG.debug("Task is obsolete");
        promise.setError("obsolete");
        return false;
      }
    }
    return true;
  }

  /**
   * This class is the {@code Invoker} in the Event Dispatch Thread,
   * which is the only one valid thread for this invoker.
   */
  public static final class EDT extends Invoker {
    private final TransferToEDTQueue<Runnable> queue;

    public EDT(@NotNull Disposable parent) {
      super("EDT", parent);
      queue = TransferToEDTQueue.createRunnableMerger(toString());
    }

    @Override
    public void dispose() {
      super.dispose();
      queue.stop();
    }

    @Override
    public boolean isValidThread() {
      return isDispatchThread();
    }

    @Override
    void offer(@NotNull Runnable runnable, int delay) {
      if (delay > 0) {
        EdtExecutorService.getScheduledExecutorInstance().schedule(runnable, delay, MILLISECONDS);
      }
      else {
        queue.offer(runnable);
      }
    }
  }

  /**
   * This class is the {@code Invoker} in a background thread pool.
   * Every thread is valid for this invoker except the EDT.
   * It allows to run background tasks in parallel,
   * but requires a good synchronization.
   */
  public static final class BackgroundPool extends Invoker {
    public BackgroundPool(@NotNull Disposable parent) {
      super("Background.Pool", parent);
    }

    @Override
    public boolean isValidThread() {
      return !isDispatchThread();
    }

    @Override
    void offer(@NotNull Runnable runnable, int delay) {
      schedule(AppExecutorUtil.getAppScheduledExecutorService(), runnable, delay);
    }
  }

  /**
   * This class is the {@code Invoker} in a single background thread.
   * This invoker does not need additional synchronization.
   */
  public static final class BackgroundThread extends Invoker {
    private final ScheduledExecutorService executor;
    private volatile Thread thread;

    public BackgroundThread(@NotNull Disposable parent) {
      super("Background.Thread", parent);
      executor = AppExecutorUtil.createBoundedScheduledExecutorService(toString(), 1);
    }

    @Override
    public void dispose() {
      super.dispose();
      executor.shutdown();
    }

    @Override
    public boolean isValidThread() {
      return thread == Thread.currentThread();
    }

    @Override
    void offer(@NotNull Runnable runnable, int delay) {
      schedule(executor, () -> {
        if (thread != null) LOG.warn("unexpected thread: " + thread);
        thread = Thread.currentThread();
        runnable.run();
        thread = null;
      }, delay);
    }
  }

  private static void schedule(ScheduledExecutorService executor, Runnable runnable, int delay) {
    if (delay > 0) {
      executor.schedule(runnable, delay, MILLISECONDS);
    }
    else {
      executor.execute(runnable);
    }
  }
}
