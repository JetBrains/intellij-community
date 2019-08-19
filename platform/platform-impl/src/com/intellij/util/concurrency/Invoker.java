// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Obsolescent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.util.Disposer.register;
import static com.intellij.openapi.util.registry.Registry.is;
import static com.intellij.util.containers.ContainerUtil.newConcurrentSet;
import static java.awt.EventQueue.isDispatchThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Sergey.Malenkov
 */
public abstract class Invoker implements Disposable {
  private static final int THRESHOLD = Integer.MAX_VALUE;
  private static final Logger LOG = Logger.getInstance(Invoker.class);
  private static final AtomicInteger UID = new AtomicInteger();
  private final ConcurrentHashMap<AsyncPromise<?>, ProgressIndicatorBase> indicators = new ConcurrentHashMap<>();
  private final AtomicInteger count = new AtomicInteger();
  private final ThreeState useReadAction;
  private final String description;
  private volatile boolean disposed;

  private Invoker(@NotNull String prefix, @NotNull Disposable parent, @NotNull ThreeState useReadAction) {
    StringBuilder sb = new StringBuilder().append(UID.getAndIncrement()).append(".Invoker.").append(prefix);
    if (useReadAction != ThreeState.UNSURE) sb.append(".ReadAction=").append(useReadAction);
    description = sb.append(": ").append(parent).toString();
    this.useReadAction = useReadAction;
    register(parent, this);
  }

  @Override
  public String toString() {
    return description;
  }

  @Override
  public void dispose() {
    disposed = true;
    while (!indicators.isEmpty()) {
      indicators.keySet().forEach(AsyncPromise::cancel);
    }
  }

  /**
   * Returns {@code true} if the current thread allows to process a task.
   *
   * @return {@code true} if the current thread is valid, or {@code false} otherwise
   */
  public boolean isValidThread() {
    if (useReadAction != ThreeState.NO) return true;
    Application application = getApplication();
    return application == null || !application.isReadAccessAllowed();
  }

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
        if (getApplication() == null) {
          task.run(); // is not interruptible in tests without application
        }
        else if (useReadAction != ThreeState.YES || isDispatchThread()) {
          ProgressManager.getInstance().runProcess(task, indicator(promise));
        }
        else if (getApplication().isReadAccessAllowed()) {
          if (((ApplicationEx)getApplication()).isWriteActionPending()) {
            offerRestart(task, promise, attempt);
            return;
          }
          ProgressManager.getInstance().runProcess(task, indicator(promise));
        }
        else {
          // try to execute a task until it stops throwing ProcessCanceledException
          while (!ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(task, indicator(promise))) {
            if (!is("invoker.can.yield.to.pending.write.actions")) {
              offerRestart(task, promise, attempt);
              return;
            }
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
    catch (ProcessCanceledException | IndexNotReadyException exception) {
      offerRestart(task, promise, attempt);
    }
    catch (Throwable throwable) {
      try {
        LOG.error(throwable);
      }
      finally {
        promise.setError(throwable);
      }
    }
    finally {
      count.decrementAndGet();
    }
  }

  /**
   * @param task    a task to execute on the valid thread
   * @param promise an object to control task processing
   * @param attempt an attempt to run the specified task
   */
  private void offerRestart(@NotNull Runnable task, @NotNull AsyncPromise<?> promise, int attempt) {
    if (canRestart(task, promise, attempt)) {
      count.incrementAndGet();
      offer(() -> invokeSafely(task, promise, attempt + 1), 10);
      LOG.debug("Task is restarted");
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

  @NotNull
  private ProgressIndicatorBase indicator(@NotNull AsyncPromise<?> promise) {
    ProgressIndicatorBase indicator = indicators.get(promise);
    if (indicator == null) {
      indicator = new ProgressIndicatorBase(true, false);
      ProgressIndicatorBase old = indicators.put(promise, indicator);
      if (old != null) LOG.error("the same task is running in parallel");
      promise.onProcessed(done -> indicators.remove(promise).cancel());
    }
    return indicator;
  }

  /**
   * This class is the {@code Invoker} in the Event Dispatch Thread,
   * which is the only one valid thread for this invoker.
   */
  public static final class EDT extends Invoker {
    public EDT(@NotNull Disposable parent) {
      super("EDT", parent, ThreeState.UNSURE);
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
        EdtExecutorService.getInstance().execute(runnable);
      }
    }
  }

  /**
   * This class is the {@code Invoker} in a background thread pool.
   * Every thread is valid for this invoker except the EDT.
   * It allows to run background tasks in parallel,
   * but requires a good synchronization.
   * @deprecated use {@link Background#Background(Disposable, int)} instead
   */
  @Deprecated
  @ScheduledForRemoval(inVersion = "2020.1")
  public static final class BackgroundPool extends Invoker {
    public BackgroundPool(@NotNull Disposable parent) {
      super("Background.Pool", parent, ThreeState.YES);
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
   * @deprecated use {@link Background#Background(Disposable)} instead
   */
  @Deprecated
  @ScheduledForRemoval(inVersion = "2021.1")
  public static final class BackgroundThread extends Invoker {
    private final ScheduledExecutorService executor;
    private volatile Thread thread;

    public BackgroundThread(@NotNull Disposable parent) {
      super("Background.Thread", parent, ThreeState.YES);
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
        if (thread != null) LOG.error("unexpected thread: " + thread);
        try {
          thread = Thread.currentThread();
          runnable.run(); // may throw an assertion error
        }
        finally {
          thread = null;
        }
      }, delay);
    }
  }

  public static final class Background extends Invoker {
    private final Set<Thread> threads = newConcurrentSet();
    private final ScheduledExecutorService executor;

    /**
     * Creates the invoker of user read actions on a background thread.
     *
     * @param parent a disposable parent object
     */
    public Background(@NotNull Disposable parent) {
      this(parent, true);
    }

    /**
     * Creates the invoker of user read actions on background threads.
     *
     * @param parent     a disposable parent object
     * @param maxThreads the number of threads used for parallel calculation,
     *                   where 1 guarantees sequential calculation,
     *                   which allows not to use additional synchronization
     */
    public Background(@NotNull Disposable parent, int maxThreads) {
      this(parent, ThreeState.YES, maxThreads);
    }

    /**
     * Creates the invoker of user tasks on a background thread.
     *
     * @param parent        a disposable parent object
     * @param useReadAction {@code true} to run user tasks as read actions with write action priority,
     *                      {@code false} to run user tasks without read locks
     */
    public Background(@NotNull Disposable parent, boolean useReadAction) {
      this(parent, ThreeState.fromBoolean(useReadAction));
    }

    /**
     * Creates the invoker of user tasks on a background thread.
     *
     * @param parent        a disposable parent object
     * @param useReadAction {@code YES} to run user tasks as read actions with write action priority,
     *                      {@code NO} to run user tasks without read locks,
     *                      {@code UNSURE} does not guarantee that read action is allowed
     */
    public Background(@NotNull Disposable parent, @NotNull ThreeState useReadAction) {
      this(parent, useReadAction, 1);
    }

    /**
     * Creates the invoker of user tasks on background threads.
     *
     * @param parent        a disposable parent object
     * @param useReadAction {@code YES} to run user tasks as read actions with write action priority,
     *                      {@code NO} to run user tasks without read locks,
     *                      {@code UNSURE} does not guarantee that read action is allowed
     * @param maxThreads    the number of threads used for parallel calculation,
     *                      where 1 guarantees sequential calculation,
     *                      which allows not to use additional synchronization
     */
    public Background(@NotNull Disposable parent, @NotNull ThreeState useReadAction, int maxThreads) {
      super(maxThreads != 1 ? "Pool(" + maxThreads + ")" : "Thread", parent, useReadAction);
      executor = AppExecutorUtil.createBoundedScheduledExecutorService(toString(), maxThreads);
    }

    @Override
    public void dispose() {
      super.dispose();
      executor.shutdown();
    }

    @Override
    public boolean isValidThread() {
      return threads.contains(Thread.currentThread()) && super.isValidThread();
    }

    @Override
    void offer(@NotNull Runnable runnable, int delay) {
      schedule(executor, () -> {
        Thread thread = Thread.currentThread();
        if (!threads.add(thread)) {
          LOG.error("current thread is already used");
        }
        else {
          try {
            runnable.run(); // may throw an assertion error
          }
          finally {
            if (!threads.remove(thread)) {
              LOG.error("current thread is already removed");
            }
          }
        }
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
