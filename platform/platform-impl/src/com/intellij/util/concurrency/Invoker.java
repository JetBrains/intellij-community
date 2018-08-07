// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.TransferToEDTQueue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Obsolescent;

import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    Disposer.register(parent, this);
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
   */
  @NotNull
  public final Future<?> invokeLater(@NotNull Runnable task) {
    return invokeLater(task, 0);
  }

  /**
   * Invokes the specified task on the valid thread after the specified delay.
   *
   * @param task  a task to execute asynchronously on the valid thread
   * @param delay milliseconds for the initial delay
   */
  @NotNull
  public final Future<?> invokeLater(@NotNull Runnable task, int delay) {
    if (delay < 0) throw new IllegalArgumentException("delay must be non-negative: "+delay);
    if (canInvoke(task)) {
      count.incrementAndGet();
      CompletableFuture<?> future = new CompletableFuture<>();
      offer(() -> invokeSafely(task, 0, future), delay);
      return future;
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Invokes the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   */
  @NotNull
  public final Future<?> runOrInvokeLater(@NotNull Runnable task) {
    if (isValidThread()) {
      count.incrementAndGet();
      CompletableFuture<?> future = new CompletableFuture<>();
      invokeSafely(task, 0, future);
      return future;
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

  private void invokeSafely(@NotNull Runnable task, int attempt, @NotNull CompletableFuture<?> futureToComplete) {
    try {
      if (canInvoke(task)) {
        if (EventQueue.isDispatchThread() || ApplicationManager.getApplication() == null) {
          // do not care about ReadAction in EDT and in tests without application
          task.run();
        }
        else if (ApplicationManager.getApplication().isReadAccessAllowed()) {
          if (((ApplicationEx)ApplicationManager.getApplication()).isWriteActionPending()) throw new ProcessCanceledException();
          task.run();
        }
        else {
          // try to execute a task until it stops throwing ProcessCanceledException
          while (!ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(task)) {
            if (!canInvoke(task)) break; // stop execution of obsolete task
            ProgressIndicatorUtils.yieldToPendingWriteActions();
            if (!canRestart(task, attempt)) break;
            LOG.debug("Task is restarted");
            attempt++;
          }
        }
      }
      futureToComplete.complete(null);
    }
    catch (ProcessCanceledException exception) {
      if (canRestart(task, attempt)) {
        count.incrementAndGet();
        int nextAttempt = attempt + 1;
        offer(() -> invokeSafely(task, nextAttempt, futureToComplete), 10);
        LOG.debug("Task is restarted");
      }
    }
    catch (Exception exception) {
      futureToComplete.completeExceptionally(exception);
      LOG.warn(exception);
    }
    catch (Throwable throwable) {
      futureToComplete.completeExceptionally(throwable);
      LOG.warn(throwable);
      throw throwable;
    }
    finally {
      count.decrementAndGet();
    }
  }

  private boolean canRestart(@NotNull Runnable task, int attempt) {
    LOG.debug("Task is canceled");
    if (attempt < THRESHOLD) return canInvoke(task);
    LOG.warn("Task is always canceled: " + task);
    return false;
  }

  private boolean canInvoke(@NotNull Runnable task) {
    if (disposed) {
      LOG.debug("Invoker is disposed");
      return false;
    }
    if (task instanceof Obsolescent) {
      Obsolescent obsolescent = (Obsolescent)task;
      if (obsolescent.isObsolete()) {
        LOG.debug("Task is obsolete");
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
      return EventQueue.isDispatchThread();
    }

    @Override
    void offer(@NotNull Runnable runnable, int delay) {
      if (delay > 0) {
        EdtExecutorService.getScheduledExecutorInstance().schedule(runnable, delay, TimeUnit.MILLISECONDS);
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
      return !EventQueue.isDispatchThread();
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
      executor.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    }
    else {
      executor.execute(runnable);
    }
  }
}
