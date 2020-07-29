// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 * Most methods in this class are used to equip long background processes which take read actions with a special listener
 * that fires when a write action is about to begin, and cancels corresponding progress indicators to avoid blocking the UI.
 * These processes should be ready to get {@link ProcessCanceledException} at any moment.
 * Processes may want to react on cancellation event by restarting the activity, see
 * {@link ReadTask#onCanceled(ProgressIndicator)} for that.
 *
 * @author gregsh
 */
public final class ProgressIndicatorUtils {
  private static final Logger LOG = Logger.getInstance(ProgressIndicatorUtils.class);

  @NotNull
  public static ProgressIndicator forceWriteActionPriority(@NotNull ProgressIndicator progress, @NotNull Disposable parentDisposable) {
    ApplicationManager.getApplication().addApplicationListener(new ApplicationListener() {
        @Override
        public void beforeWriteActionStart(@NotNull Object action) {
          if (progress.isRunning()) {
            progress.cancel();
          }
        }
      }, parentDisposable);
    return progress;
  }

  public static void scheduleWithWriteActionPriority(@NotNull ReadTask task) {
    scheduleWithWriteActionPriority(new ProgressIndicatorBase(false, false), task);
  }

  @NotNull
  public static CompletableFuture<?> scheduleWithWriteActionPriority(@NotNull ProgressIndicator progressIndicator, @NotNull ReadTask readTask) {
    return scheduleWithWriteActionPriority(progressIndicator, PooledThreadExecutor.INSTANCE, readTask);
  }

  /**
   * Same as {@link #runInReadActionWithWriteActionPriority(Runnable)}, optionally allowing to pass a {@link ProgressIndicator}
   * instance, which can be used to cancel action externally.
   */
  public static boolean runInReadActionWithWriteActionPriority(@NotNull final Runnable action,
                                                               @Nullable ProgressIndicator progressIndicator) {
    final Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    runWithWriteActionPriority(() -> result.set(ApplicationManagerEx.getApplicationEx().tryRunReadAction(action)),
                               progressIndicator == null ? new ProgressIndicatorBase(false, false) : progressIndicator);
    return result.get();
  }

  /**
   * This method attempts to run provided action synchronously in a read action, so that, if possible, it wouldn't impact any pending,
   * executing or future write actions (for this to work effectively the action should invoke {@link ProgressManager#checkCanceled()} or
   * {@link ProgressIndicator#checkCanceled()} often enough).
   * It returns {@code true} if action was executed successfully. It returns {@code false} if the action was not
   * executed successfully, i.e. if:
   * <ul>
   * <li>write action was in progress when the method was called</li>
   * <li>write action was pending when the method was called</li>
   * <li>action started to execute, but was aborted using {@link ProcessCanceledException} when some other thread initiated
   * write action</li>
   * </ul>
   * If caller needs to retry the invocation of this method in a loop, it should consider pausing between attempts, to avoid potential
   * 100% CPU usage. There is also alternative that implements the re-trying logic {@link com.intellij.openapi.application.NonBlockingReadAction}
   */
  public static boolean runInReadActionWithWriteActionPriority(@NotNull final Runnable action) {
    return runInReadActionWithWriteActionPriority(action, null);
  }

  public static boolean runWithWriteActionPriority(@NotNull Runnable action, @NotNull ProgressIndicator progressIndicator) {
    ApplicationEx application = (ApplicationEx)ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      throw new IllegalStateException("Must not call from EDT");
    }
    Runnable cancellation = indicatorCancellation(progressIndicator);
    if (isWriting(application)) {
      cancellation.run();
      return false;
    }
    return ProgressManager.getInstance().runProcess(() -> {
      try {
        // add listener inside runProcess to avoid cancelling indicator before even starting the progress
        return runActionAndCancelBeforeWrite(application, cancellation, action);
      }
      catch (ProcessCanceledException ignore) {
        return false;
      }
    }, progressIndicator);
  }

  private static final List<Runnable> ourWACancellations = ContainerUtil.createLockFreeCopyOnWriteList();

  static {
    Application app = ApplicationManager.getApplication();
    app.addApplicationListener(new ApplicationListener() {
      @Override
      public void beforeWriteActionStart(@NotNull Object action) {
        for (Runnable cancellation : ourWACancellations) {
          cancellation.run();
        }
      }
    }, app);
  }

  @ApiStatus.Internal
  public static boolean runActionAndCancelBeforeWrite(@NotNull ApplicationEx application,
                                                      @NotNull Runnable cancellation,
                                                      @NotNull Runnable action) {
    if (isWriting(application)) {
      cancellation.run();
      return false;
    }

    ourWACancellations.add(cancellation);
    try {
      if (isWriting(application)) {
        // the listener might not be notified if write action was requested concurrently with listener addition
        cancellation.run();
        return false;
      }
      else {
        action.run();
        return true;
      }
    }
    finally {
      ourWACancellations.remove(cancellation);
    }
  }

  private static @NotNull Runnable indicatorCancellation(@NotNull ProgressIndicator progressIndicator) {
    return () -> {
      if (!progressIndicator.isCanceled()) {
        progressIndicator.cancel();
      }
    };
  }

  private static boolean isWriting(ApplicationEx application) {
    return application.isWriteActionPending() || application.isWriteActionInProgress();
  }

  @NotNull
  public static CompletableFuture<?> scheduleWithWriteActionPriority(@NotNull final ProgressIndicator progressIndicator,
                                                                     @NotNull final Executor executor,
                                                                     @NotNull final ReadTask readTask) {
    // invoke later even if on EDT
    // to avoid tasks eagerly restarting immediately, allocating many pooled threads
    // which get cancelled too soon when a next write action arrives in the same EDT batch
    // (can happen when processing multiple VFS events or writing multiple files on save)

    CompletableFuture<?> future = new CompletableFuture<>();
    Application application = ApplicationManager.getApplication();
    application.invokeLater(() -> {
      if (application.isDisposed() || progressIndicator.isCanceled() || future.isCancelled()) {
        future.complete(null);
        return;
      }
      Disposable listenerDisposable = Disposer.newDisposable();
      final ApplicationListener listener = new ApplicationListener() {
        @Override
        public void beforeWriteActionStart(@NotNull Object action) {
          if (!progressIndicator.isCanceled()) {
            progressIndicator.cancel();
            readTask.onCanceled(progressIndicator);
          }
        }
      };
      application.addApplicationListener(listener, listenerDisposable);
      future.whenComplete((__, ___) -> Disposer.dispose(listenerDisposable));
      try {
        executor.execute(ClientId.decorateRunnable(new Runnable() {
          @Override
          public void run() {
            final ReadTask.Continuation continuation;
            try {
              continuation = runUnderProgress(progressIndicator, readTask);
            }
            catch (Throwable e) {
              future.completeExceptionally(e);
              throw e;
            }
            if (continuation == null) {
              future.complete(null);
            }
            else if (!future.isCancelled()) {
              application.invokeLater(new Runnable() {
                @Override
                public void run() {
                  if (future.isCancelled()) return;

                    Disposer.dispose(listenerDisposable); // remove listener early to prevent firing it during continuation execution
                    try {
                      if (!progressIndicator.isCanceled()) {
                        continuation.getAction().run();
                      }
                    }
                    finally {
                      future.complete(null);
                    }
                  }

                  @Override
                  public String toString() {
                    return "continuation of " + readTask;
                  }
                }, continuation.getModalityState());
              }

          }

          @Override
          public String toString() {
            return readTask.toString();
          }
        }));
      }
      catch (Throwable e) {
        future.completeExceptionally(e);
        throw e;
      }
    }, ModalityState.any()); // 'any' to tolerate immediate modality changes (e.g. https://youtrack.jetbrains.com/issue/IDEA-135180)
    return future;
  }

  private static ReadTask.Continuation runUnderProgress(@NotNull final ProgressIndicator progressIndicator, @NotNull final ReadTask task) {
    return ProgressManager.getInstance().runProcess(() -> {
      try {
        return task.runBackgroundProcess(progressIndicator);
      }
      catch (ProcessCanceledException ignore) {
        return null;
      }
    }, progressIndicator);
  }

  /**
   * Ensure the current EDT activity finishes in case it requires many write actions, with each being delayed a bit
   * by background thread read action (until its first checkCanceled call). Shouldn't be called from under read action.
   */
  public static void yieldToPendingWriteActions() {
    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed()) {
      throw new IllegalStateException("Mustn't be called from within read action");
    }
    if (application.isDispatchThread()) {
      throw new IllegalStateException("Mustn't be called from EDT");
    }
    Semaphore semaphore = new Semaphore(1);
    application.invokeLater(semaphore::up, ModalityState.any());
    awaitWithCheckCanceled(semaphore, ProgressIndicatorProvider.getGlobalProgressIndicator());
  }

  /**
   * Run the given computation with its execution time restricted to the given amount of time in milliseconds.<p></p>
   *
   * Internally, it creates a new {@link ProgressIndicator}, runs the computation with that indicator and cancels it after the the timeout.
   * The computation should call {@link ProgressManager#checkCanceled()} frequently enough, so that after the timeout has been exceeded
   * it can stop the execution by throwing {@link ProcessCanceledException}, which will be caught by this {@code withTimeout}.<p></p>
   *
   * If a {@link ProcessCanceledException} happens due to any other reason (e.g. a thread's progress indicator got canceled),
   * it'll be thrown out of this method.
   * @return the computation result or {@code null} if timeout has been exceeded.
   */
  @Nullable
  public static <T> T withTimeout(long timeoutMs, @NotNull Computable<T> computation) {
    ProgressManager.checkCanceled();
    ProgressIndicator outer = ProgressIndicatorProvider.getGlobalProgressIndicator();
    ProgressIndicator inner = outer != null ? new SensitiveProgressWrapper(outer) : new ProgressIndicatorBase(false, false);
    AtomicBoolean canceledByTimeout = new AtomicBoolean();
    ScheduledFuture<?> cancelProgress = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
      canceledByTimeout.set(true);
      inner.cancel();
    }, timeoutMs, TimeUnit.MILLISECONDS);
    try {
      return ProgressManager.getInstance().runProcess(computation, inner);
    }
    catch (ProcessCanceledException e) {
      if (canceledByTimeout.get()) {
        return null;
      }
      throw e; // canceled not by timeout
    }
    finally {
      cancelProgress.cancel(false);
    }
  }

  public static <T, E extends Throwable> T computeWithLockAndCheckingCanceled(@NotNull Lock lock,
                                                                              int timeout,
                                                                              @NotNull TimeUnit timeUnit,
                                                                              @NotNull ThrowableComputable<T, E> computable) throws E, ProcessCanceledException {
    awaitWithCheckCanceled(lock, timeout, timeUnit);

    try {
      return computable.compute();
    }
    finally {
      lock.unlock();
    }
  }

  public static void awaitWithCheckCanceled(@NotNull CountDownLatch waiter) {
    awaitWithCheckCanceled(() -> waiter.await(10, TimeUnit.MILLISECONDS));
  }

  public static <T> T awaitWithCheckCanceled(@NotNull Future<T> future) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    return awaitWithCheckCanceled(future, indicator);
  }

  public static <T> T awaitWithCheckCanceled(@NotNull Future<T> future, @Nullable ProgressIndicator indicator) {
    while (true) {
      checkCancelledEvenWithPCEDisabled(indicator);
      try {
        return future.get(10, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException | RejectedExecutionException ignore) {
      }
      catch (InterruptedException e) {
        throw new ProcessCanceledException(e);
      }
      catch (Throwable e) {
        Throwable cause = e.getCause();
        if (cause instanceof ProcessCanceledException) {
          throw (ProcessCanceledException)cause;
        }
        if (cause instanceof CancellationException) {
          throw new ProcessCanceledException(cause);
        }
        ExceptionUtil.rethrow(e);
      }
    }
  }

  public static void awaitWithCheckCanceled(@NotNull Lock lock, int timeout, @NotNull TimeUnit timeUnit) {
    awaitWithCheckCanceled(() -> lock.tryLock(timeout, timeUnit));
  }

  public static void awaitWithCheckCanceled(@NotNull ThrowableComputable<Boolean, ? extends Exception> waiter) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    boolean success = false;
    while (!success) {
      checkCancelledEvenWithPCEDisabled(indicator);
      try {
        success = waiter.compute();
      }
      catch (Exception e) {
        //noinspection InstanceofCatchParameter
        if (!(e instanceof InterruptedException)) {
          LOG.warn(e);
        }
        throw new ProcessCanceledException(e);
      }
    }
  }

  /** Use when otherwise a deadlock is possible. */
  public static void checkCancelledEvenWithPCEDisabled(@Nullable ProgressIndicator indicator) {
    if (indicator != null && indicator.isCanceled()) {
      indicator.checkCanceled(); // maybe it'll throw with some useful additional information
      throw new ProcessCanceledException();
    }
  }

  public static void awaitWithCheckCanceled(@NotNull Semaphore semaphore, @Nullable ProgressIndicator indicator) {
    while (!semaphore.waitFor(10)) {
      checkCancelledEvenWithPCEDisabled(indicator);
    }
  }
}
