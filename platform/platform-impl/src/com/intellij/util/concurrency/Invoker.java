// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Obsolescent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.progress.util.ProgressIndicatorUtils.runInReadActionWithWriteActionPriority;
import static com.intellij.openapi.util.Disposer.register;
import static com.intellij.util.containers.ContainerUtil.newConcurrentSet;
import static java.awt.EventQueue.isDispatchThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public abstract class Invoker implements Disposable {
  private static final int THRESHOLD = Integer.MAX_VALUE;
  private static final Logger LOG = Logger.getInstance(Invoker.class);
  private static final AtomicInteger UID = new AtomicInteger();
  private final Map<AsyncPromise<?>, ProgressIndicatorBase> indicators = new ConcurrentHashMap<>();
  private final AtomicInteger count = new AtomicInteger();
  private final ThreeState useReadAction;
  private final String description;
  private volatile boolean disposed;

  private Invoker(@NotNull String prefix, @NotNull Disposable parent, @NotNull ThreeState useReadAction) {
    description = "Invoker." + UID.getAndIncrement()+"."+prefix + (useReadAction != ThreeState.UNSURE ? ".ReadAction="+useReadAction : "") + ": "+parent;
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
   * Computes the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   * @return an object to control task processing
   */
  @NotNull
  public final <T> CancellablePromise<T> compute(@NotNull Supplier<? extends T> task) {
    return promise(new Task<>(task));
  }

  /**
   * Computes the specified task asynchronously on the valid thread.
   * Even if this method is called from the valid thread
   * the specified task will still be deferred
   * until all pending events have been processed.
   *
   * @param task a task to execute asynchronously on the valid thread
   * @return an object to control task processing
   */
  @NotNull
  public final <T> CancellablePromise<T> computeLater(@NotNull Supplier<? extends T> task) {
    return computeLater(task, 0);
  }

  /**
   * Computes the specified task on the valid thread after the specified delay.
   *
   * @param task  a task to execute asynchronously on the valid thread
   * @param delay milliseconds for the initial delay
   * @return an object to control task processing
   */
  @NotNull
  public final <T> CancellablePromise<T> computeLater(@NotNull Supplier<? extends T> task, int delay) {
    return promise(new Task<>(task), delay);
  }

  /**
   * Invokes the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   * @return an object to control task processing
   */
  @NotNull
  public final CancellablePromise<?> invoke(@NotNull Runnable task) {
    return compute(new Wrapper(task));
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
    return computeLater(new Wrapper(task), delay);
  }

  /**
   * Invokes the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   * @return an object to control task processing
   * @deprecated use {@link #invoke(Runnable)} or {@link #compute(Supplier)} instead
   */
  @NotNull
  @Deprecated
  @ScheduledForRemoval(inVersion = "2021.1")
  public final CancellablePromise<?> runOrInvokeLater(@NotNull Runnable task) {
    return invoke(task);
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
   * @param attempt an attempt to run the specified task
   * @param delay   milliseconds for the initial delay
   */
  private void offerSafely(@NotNull Task<?> task, int attempt, int delay) {
    try {
      count.incrementAndGet();
      offer(() -> invokeSafely(task, attempt), delay);
    }
    catch (RejectedExecutionException exception) {
      count.decrementAndGet();
      LOG.debug("Executor is shutdown");
      task.promise.setError("shutdown");
    }
  }

  /**
   * @param task    a task to execute on the valid thread
   * @param attempt an attempt to run the specified task
   */
  private void invokeSafely(@NotNull Task<?> task, int attempt) {
    try {
      if (task.canInvoke(disposed)) {
        if (getApplication() == null) {
          task.run(); // is not interruptible in tests without application
        }
        else if (useReadAction != ThreeState.YES || isDispatchThread()) {
          ProgressManager.getInstance().runProcess(task, indicator(task.promise));
        }
        else if (!runInReadActionWithWriteActionPriority(task, indicator(task.promise))) {
          offerRestart(task, attempt);
          return;
        }
        task.setResult();
      }
    }
    catch (ProcessCanceledException | IndexNotReadyException exception) {
      offerRestart(task, attempt);
    }
    catch (Throwable throwable) {
      try {
        LOG.error(throwable);
      }
      finally {
        task.promise.setError(throwable);
      }
    }
    finally {
      count.decrementAndGet();
    }
  }

  /**
   * @param task    a task to execute on the valid thread
   * @param attempt an attempt to run the specified task
   */
  private void offerRestart(@NotNull Task<?> task, int attempt) {
    if (task.canRestart(disposed, attempt)) {
      offerSafely(task, attempt + 1, 10);
      LOG.debug("Task is restarted");
    }
  }

  /**
   * Promises to invoke the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   * @return an object to control task processing
   */
  @NotNull
  private <T> CancellablePromise<T> promise(@NotNull Task<T> task) {
    if (!isValidThread()) return promise(task, 0);
    count.incrementAndGet();
    invokeSafely(task, 0);
    return task.promise;
  }

  /**
   * Promises to invoke the specified task on the valid thread after the specified delay.
   *
   * @param task  a task to execute on the valid thread
   * @param delay milliseconds for the initial delay
   * @return an object to control task processing
   */
  @NotNull
  private <T> CancellablePromise<T> promise(@NotNull Task<T> task, int delay) {
    if (delay < 0) throw new IllegalArgumentException("delay must be non-negative: " + delay);
    if (task.canInvoke(disposed)) offerSafely(task, 0, delay);
    return task.promise;
  }

  /**
   * This data class is intended to combine a developer's task
   * with the corresponding object used to control its processing.
   */
  static final class Task<T> implements Runnable {
    final AsyncPromise<T> promise = new AsyncPromise<>();
    private final Supplier<? extends T> supplier;
    private volatile T result;

    Task(@NotNull Supplier<? extends T> supplier) {
      this.supplier = supplier;
    }

    boolean canRestart(boolean disposed, int attempt) {
      LOG.debug("Task is canceled");
      if (attempt < THRESHOLD) return canInvoke(disposed);
      LOG.warn("Task is always canceled: " + supplier);
      promise.setError("timeout");
      return false; // too many attempts to run the task
    }

    boolean canInvoke(boolean disposed) {
      if (promise.isDone()) {
        LOG.debug("Promise is cancelled: ", promise.isCancelled());
        return false; // the given promise is already done or cancelled
      }
      if (disposed) {
        LOG.debug("Invoker is disposed");
        promise.setError("disposed");
        return false; // the current invoker is disposed
      }
      if (supplier instanceof Obsolescent) {
        Obsolescent obsolescent = (Obsolescent)supplier;
        if (obsolescent.isObsolete()) {
          LOG.debug("Task is obsolete");
          promise.setError("obsolete");
          return false; // the specified task is obsolete
        }
      }
      return true;
    }

    void setResult() {
      promise.setResult(result);
    }

    @Override
    public void run() {
      result = supplier.get();
    }

    @Override
    public String toString() {
      return "Invoker.Task: " + supplier;
    }
  }


  /**
   * This wrapping class is intended to convert a developer's runnable to the obsolescent supplier.
   */
  private static final class Wrapper implements Obsolescent, Supplier<Void> {
    private final Runnable task;

    Wrapper(@NotNull Runnable task) {
      this.task = task;
    }

    @Override
    public Void get() {
      task.run();
      return null;
    }

    @Override
    public boolean isObsolete() {
      return task instanceof Obsolescent && ((Obsolescent)task).isObsolete();
    }

    @Override
    public String toString() {
      return task.toString();
    }
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
    /**
     * Creates the invoker of user tasks on the event dispatch thread.
     *
     * @param parent a disposable parent object
     * @deprecated use {@link #forEventDispatchThread} instead
     */
    @Deprecated
    @ScheduledForRemoval(inVersion = "2021.1")
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
   * This class is the {@code Invoker} in a single background thread.
   * This invoker does not need additional synchronization.
   *
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
     * @deprecated use {@link #forBackgroundThreadWithReadAction} instead
     */
    @Deprecated
    @ScheduledForRemoval(inVersion = "2021.1")
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
     * @deprecated use {@link #forBackgroundPoolWithReadAction} instead
     */
    @Deprecated
    @ScheduledForRemoval(inVersion = "2021.1")
    public Background(@NotNull Disposable parent, int maxThreads) {
      this(parent, ThreeState.YES, maxThreads);
    }

    /**
     * Creates the invoker of user tasks on a background thread.
     *
     * @param parent        a disposable parent object
     * @param useReadAction {@code true} to run user tasks as read actions with write action priority,
     *                      {@code false} to run user tasks without read locks
     * @deprecated use {@link #forBackgroundThreadWithReadAction} or {@link #forBackgroundThreadWithoutReadAction} instead
     */
    @Deprecated
    @ScheduledForRemoval(inVersion = "2021.1")
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
     * @deprecated use {@link #forBackgroundThreadWithReadAction} or {@link #forBackgroundThreadWithoutReadAction} instead
     */
    @Deprecated
    @ScheduledForRemoval(inVersion = "2021.1")
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
     * @deprecated use {@link #forBackgroundThreadWithReadAction} or {@link #forBackgroundThreadWithoutReadAction} instead
     */
    @Deprecated
    @ScheduledForRemoval(inVersion = "2021.1")
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


  @NotNull
  public static Invoker forEventDispatchThread(@NotNull Disposable parent) {
    return new EDT(parent);
  }

  @NotNull
  public static Invoker forBackgroundPoolWithReadAction(@NotNull Disposable parent) {
    return new Background(parent, ThreeState.YES, 8);
  }

  @NotNull
  public static Invoker forBackgroundThreadWithReadAction(@NotNull Disposable parent) {
    return new Background(parent, ThreeState.YES, 1);
  }

  @NotNull
  public static Invoker forBackgroundThreadWithoutReadAction(@NotNull Disposable parent) {
    return new Background(parent, ThreeState.NO, 1);
  }
}
