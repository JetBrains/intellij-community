// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Obsolescent;
import org.jetbrains.concurrency.Promise;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static java.awt.EventQueue.isDispatchThread;

public abstract class Invoker implements Disposable {
  private static final int THRESHOLD = Integer.MAX_VALUE;
  static final Logger LOG = Logger.getInstance(Invoker.class);
  private static final AtomicInteger UID = new AtomicInteger();
  final InvokerDelegate delegate;
  private final AtomicInteger count = new AtomicInteger();
  private final ThreeState useReadAction;
  private volatile boolean disposed;

  private Invoker(@NotNull InvokerDelegate delegate, @NotNull ThreeState useReadAction) {
    this.delegate = delegate;
    Disposer.register(this, this.delegate);
    this.useReadAction = useReadAction;
  }

  private static @NotNull String newDescription(@NotNull String prefix, @NotNull String parentName, @NotNull ThreeState useReadAction) {
    final String description;
    String readActionDescriptionPart = useReadAction != ThreeState.UNSURE ? ".ReadAction=" + useReadAction : "";
    description = "Invoker." + UID.getAndIncrement() + "." + prefix + readActionDescriptionPart + ": " + parentName;
    return description;
  }

  @Override
  public String toString() {
    return delegate.getDescription();
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
  public final @NotNull <T> CancellablePromise<T> compute(@NotNull Supplier<? extends T> task) {
    return promise(new Task.Sync<>(task));
  }

  /**
   * Computes the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   * @return an object to control task processing
   */
  public final @NotNull <T> CancellablePromise<T> computeAsync(@NotNull Supplier<Promise<? extends T>> task) {
    return promise(new Task.Async<>(task));
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
  public final @NotNull <T> CancellablePromise<T> computeLater(@NotNull Supplier<? extends T> task) {
    return promise(new Task.Sync<>(task), 0);
  }

  /**
   * Invokes the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   * @return an object to control task processing
   */
  public final @NotNull CancellablePromise<?> invoke(@NotNull Runnable task) {
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
  public final @NotNull CancellablePromise<?> invokeLater(@NotNull Runnable task) {
    return invokeLater(task, 0);
  }

  /**
   * Invokes the specified task on the valid thread after the specified delay.
   *
   * @param task  a task to execute asynchronously on the valid thread
   * @param delay milliseconds for the initial delay
   * @return an object to control task processing
   */
  public final @NotNull CancellablePromise<?> invokeLater(@NotNull Runnable task, int delay) {
    return promise(new Task.Sync<>(new Wrapper(task)), delay);
  }

  /**
   * Invokes the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   * @return an object to control task processing
   * @deprecated use {@link #invoke(Runnable)} or {@link #compute(Supplier)} instead
   */
  @Deprecated(forRemoval = true)
  public final @NotNull CancellablePromise<?> runOrInvokeLater(@NotNull Runnable task) {
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

  abstract void offer(@NotNull Runnable runnable, int delay, Promise<?> promise);

  /**
   * @param task    a task to execute on the valid thread
   * @param attempt an attempt to run the specified task
   * @param delay   milliseconds for the initial delay
   */
  private void offerSafely(@NotNull Task<?, ?> task, int attempt, int delay) {
    try {
      count.incrementAndGet();
      offer(() -> invokeSafely(task, attempt), delay, task.promise);
    }
    catch (RejectedExecutionException exception) {
      count.decrementAndGet();
      if (LOG.isTraceEnabled()) LOG.debug("Executor is shutdown");
      task.promise.setError("shutdown");
    }
  }

  /**
   * @param task    a task to execute on the valid thread
   * @param attempt an attempt to run the specified task
   */
  private void invokeSafely(@NotNull Task<?, ?> task, int attempt) {
    try {
      if (task.canInvoke(disposed)) {
        if (!delegate.run(task, task.promise)) {
          offerRestart(task, attempt);
          return;
        }
        if (task instanceof Task.Async<?> t) {
          Promise<?> incomplete = t.setDone();
          if (incomplete != null) {
            count.incrementAndGet();
            incomplete
              .onError(th -> handleTaskError(task, th, attempt))
              .onProcessed(r -> count.decrementAndGet());
          }
        }
        else if (task instanceof Task.Sync<?> t) {
          t.setDone();
        }
      }
    }
    catch (Throwable throwable) {
      handleTaskError(task, throwable, attempt);
    }
    finally {
      count.decrementAndGet();
    }
  }

  private void handleTaskError(@NotNull Task<?, ?> task, @NotNull Throwable throwable, int attempt) {
    if (throwable instanceof ProcessCanceledException || throwable == AsyncPromise.CANCELED || throwable instanceof IndexNotReadyException) {
      offerRestart(task, attempt);
      return;
    }
    try {
      LOG.error(throwable);
    }
    finally {
      task.promise.setError(throwable);
    }
  }

  /**
   * @param task    a task to execute on the valid thread
   * @param attempt an attempt to run the specified task
   */
  private void offerRestart(@NotNull Task<?, ?> task, int attempt) {
    if (task.canRestart(disposed, attempt)) {
      offerSafely(task, attempt + 1, 10);
      if (LOG.isTraceEnabled()) LOG.debug("Task is restarted");
    }
  }

  /**
   * Promises to invoke the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   * @return an object to control task processing
   */
  private @NotNull <T> CancellablePromise<T> promise(@NotNull Task<T, ?> task) {
    if (!isValidThread()) {
      return promise(task, 0);
    }
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
  private @NotNull <T> CancellablePromise<T> promise(@NotNull Task<T, ?> task, int delay) {
    if (delay < 0) {
      throw new IllegalArgumentException("delay must be non-negative: " + delay);
    }
    if (task.canInvoke(disposed)) {
      offerSafely(task, 0, delay);
    }
    return task.promise;
  }

  /**
   * This data class is intended to combine a developer's task
   * with the corresponding object used to control its processing.
   */
  abstract static class Task<T, R> implements Runnable {
    final AsyncPromise<T> promise = new AsyncPromise<>();
    private final Supplier<? extends R> supplier;
    private volatile R result;

    static class Sync<T> extends Task<T, T> {
      Sync(@NotNull Supplier<? extends T> supplier) {
        super(supplier);
      }

      void setDone() {
        setDone(getResult());
      }
    }
    static class Async<T> extends Task<T, Promise<? extends T>> {
      Async(@NotNull Supplier<? extends Promise<? extends T>> supplier) {
        super(supplier);
      }

      @Nullable
      Promise<? extends T> setDone() {
        Promise<? extends T> res = getResult();
        if (res == null) {
          setDone(null);
          return null;
        }
        if (res.getState() == Promise.State.PENDING) {
          return res.onSuccess(r -> setDone(r));
        }
        try {
          setDone(res.blockingGet(0));
        }
        catch (ExecutionException e) {
          ExceptionUtil.rethrow(e.getCause());
        }
        catch (TimeoutException e) {
          ExceptionUtil.rethrow(e);
        }

        return null;
      }
    }

    Task(@NotNull Supplier<? extends R> supplier) {
      this.supplier = supplier;
    }

    boolean canRestart(boolean disposed, int attempt) {
      if (LOG.isTraceEnabled()) LOG.debug("Task is canceled");
      if (attempt < THRESHOLD) return canInvoke(disposed);
      LOG.warn("Task is always canceled: " + supplier);
      promise.setError("timeout");
      return false; // too many attempts to run the task
    }

    boolean canInvoke(boolean disposed) {
      if (promise.isDone()) {
        if (LOG.isTraceEnabled()) LOG.debug("Promise is cancelled: ", promise.isCancelled());
        return false; // the given promise is already done or cancelled
      }
      if (disposed) {
        if (LOG.isTraceEnabled()) LOG.debug("Invoker is disposed");
        promise.setError("disposed");
        return false; // the current invoker is disposed
      }
      if (supplier instanceof Obsolescent obsolescent) {
        if (obsolescent.isObsolete()) {
          if (LOG.isTraceEnabled()) LOG.debug("Task is obsolete");
          promise.setError("obsolete");
          return false; // the specified task is obsolete
        }
      }
      return true;
    }

    R getResult() {
      return result;
    }

    void setDone(T r) {
      promise.setResult(r);
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
    @Deprecated(forRemoval = true)
    public EDT(@NotNull Disposable parent) {
      super(InvokerService.getInstance().forEdt(newDescription("EDT", parent.toString(), ThreeState.UNSURE)), ThreeState.UNSURE);
      Disposer.register(parent, this);
    }

    @Override
    public boolean isValidThread() {
      return isDispatchThread();
    }

    @Override
    void offer(@NotNull Runnable runnable, int delay, Promise<?> promise) {
      delegate.offer(runnable, delay, promise);
    }
  }

  public static final class Background extends Invoker {
    private final Set<Thread> threads = ConcurrentCollectionFactory.createConcurrentSet();

    /**
     * Creates the invoker of user read actions on background threads.
     *
     * @param parent     a disposable parent object
     * @param maxThreads the number of threads used for parallel calculation,
     *                   where 1 guarantees sequential calculation,
     *                   which allows not to use additional synchronization
     * @deprecated use {@link #forBackgroundPoolWithReadAction} instead
     */
    @Deprecated(forRemoval = true)
    public Background(@NotNull Disposable parent, int maxThreads) {
      this(parent, true, maxThreads);
    }

    private Background(@NotNull Disposable parent, boolean useReadAction, int maxThreads) {
      super(
        InvokerService.getInstance().forBgt(
          newDescription(maxThreads != 1 ? "Pool(" + maxThreads + ")" : "Thread", String.valueOf(parent.toString()),
                         useReadAction ? ThreeState.YES : ThreeState.NO),
          useReadAction,
          maxThreads
        ),
        useReadAction ? ThreeState.YES : ThreeState.NO
      );
      Disposer.register(parent, this);
    }

    @Override
    public boolean isValidThread() {
      return threads.contains(Thread.currentThread()) && super.isValidThread();
    }

    @Override
    void offer(@NotNull Runnable runnable, int delay, Promise<?> promise) {
      delegate.offer(() -> {
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
      }, delay, promise);
    }
  }


  public static @NotNull Invoker forEventDispatchThread(@NotNull Disposable parent) {
    return new EDT(parent);
  }

  public static @NotNull Invoker forBackgroundPoolWithReadAction(@NotNull Disposable parent) {
    return new Background(parent, true, 8);
  }

  public static @NotNull Invoker forBackgroundPoolWithoutReadAction(@NotNull Disposable parent) {
    return new Background(parent, false, 8);
  }

  public static @NotNull Invoker forBackgroundThreadWithReadAction(@NotNull Disposable parent) {
    return new Background(parent, true, 1);
  }

  public static @NotNull Invoker forBackgroundThreadWithoutReadAction(@NotNull Disposable parent) {
    return new Background(parent, false, 1);
  }
}
