// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.constraints.ExpirableConstrainedExecution;
import com.intellij.openapi.application.constraints.Expiration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import kotlin.collections.ArraysKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * @author peter
 */
@VisibleForTesting
public class NonBlockingReadActionImpl<T>
  extends ExpirableConstrainedExecution<NonBlockingReadActionImpl<T>>
  implements NonBlockingReadAction<T> {
  private static final Logger LOG = Logger.getInstance(NonBlockingReadActionImpl.class);

  private final @Nullable Pair<ModalityState, Consumer<T>> myEdtFinish;
  private final @Nullable List<Object> myCoalesceEquality;
  private final Callable<T> myComputation;

  private static final Set<CancellablePromise<?>> ourTasks = ContainerUtil.newConcurrentSet();
  private static final Map<List<Object>, CancellablePromise<?>> ourTasksByEquality = ContainerUtil.newConcurrentMap();
  private static final AtomicInteger ourUnboundedSubmissionCount = new AtomicInteger();

  NonBlockingReadActionImpl(@NotNull Callable<T> computation) {
    this(computation, null, new ContextConstraint[0], new BooleanSupplier[0], Collections.emptySet(), null);
  }

  private NonBlockingReadActionImpl(@NotNull Callable<T> computation,
                                    @Nullable Pair<ModalityState, Consumer<T>> edtFinish,
                                    @NotNull ContextConstraint[] constraints,
                                    @NotNull BooleanSupplier[] cancellationConditions,
                                    @NotNull Set<? extends Expiration> expirationSet,
                                    @Nullable List<Object> coalesceEquality) {
    super(constraints, cancellationConditions, expirationSet);
    myComputation = computation;
    myEdtFinish = edtFinish;
    myCoalesceEquality = coalesceEquality;
  }

  @NotNull
  @Override
  protected NonBlockingReadActionImpl<T> cloneWith(@NotNull ContextConstraint[] constraints,
                                                   @NotNull BooleanSupplier[] cancellationConditions,
                                                   @NotNull Set<? extends Expiration> expirationSet) {
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, constraints, cancellationConditions, expirationSet,
                                           myCoalesceEquality);
  }

  @Override
  public void dispatchLaterUnconstrained(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
  }

  @Override
  public NonBlockingReadAction<T> inSmartMode(@NotNull Project project) {
    return withConstraint(new InSmartMode(project), project);
  }

  @Override
  public NonBlockingReadAction<T> withDocumentsCommitted(@NotNull Project project) {
    return withConstraint(new WithDocumentsCommitted(project, ModalityState.any()), project);
  }

  @Override
  public NonBlockingReadAction<T> expireWhen(@NotNull BooleanSupplier expireCondition) {
    return cancelIf(expireCondition);
  }

  @Override
  public NonBlockingReadAction<T> finishOnUiThread(@NotNull ModalityState modality, @NotNull Consumer<T> uiThreadAction) {
    return new NonBlockingReadActionImpl<>(myComputation, Pair.create(modality, uiThreadAction),
                                           getConstraints(), getCancellationConditions(), getExpirationSet(), myCoalesceEquality);
  }

  @Override
  public NonBlockingReadAction<T> coalesceBy(@NotNull Object... equality) {
    if (myCoalesceEquality != null) throw new IllegalStateException("Setting equality twice is not allowed");
    if (equality.length == 0) throw new IllegalArgumentException("Equality should include at least one object");
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, getConstraints(), getCancellationConditions(), getExpirationSet(),
                                           ContainerUtil.newArrayList(equality));
  }

  @Override
  public CancellablePromise<T> submit(@NotNull Executor backgroundThreadExecutor) {
    AsyncPromise<T> promise = new AsyncPromise<>();
    trackSubmission(backgroundThreadExecutor, promise);
    new Submission(promise, backgroundThreadExecutor).transferToBgThread();
    return promise;
  }

  private void trackSubmission(@NotNull Executor backgroundThreadExecutor, AsyncPromise<T> promise) {
    if (myCoalesceEquality != null) {
      setupCoalescing(promise, myCoalesceEquality);
    }
    if (backgroundThreadExecutor == AppExecutorUtil.getAppExecutorService()) {
      preventTooManySubmissions(promise);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      rememberSubmissionInTests(promise);
    }
  }

  private static void setupCoalescing(AsyncPromise<?> promise, List<Object> coalesceEquality) {
    CancellablePromise<?> previous = ourTasksByEquality.put(coalesceEquality, promise);
    if (previous != null) {
      previous.cancel();
    }
    promise.onProcessed(__ -> ourTasksByEquality.remove(coalesceEquality, promise));
  }

  private static void preventTooManySubmissions(AsyncPromise<?> promise) {
    if (ourUnboundedSubmissionCount.incrementAndGet() % 100 == 0) {
      LOG.error("Too many non-blocking read actions submitted at once");
    }
    promise.onProcessed(__ -> ourUnboundedSubmissionCount.decrementAndGet());
  }

  private static void rememberSubmissionInTests(AsyncPromise<?> promise) {
    ourTasks.add(promise);
    promise.onProcessed(__ -> ourTasks.remove(promise));
  }

  private class Submission {
    private final AsyncPromise<? super T> promise;
    @NotNull private final Executor backendExecutor;
    private volatile ProgressIndicator currentIndicator;
    private final ModalityState creationModality = ModalityState.defaultModalityState();
    @Nullable private final BooleanSupplier myExpireCondition;

    Submission(AsyncPromise<? super T> promise, @NotNull Executor backgroundThreadExecutor) {
      this.promise = promise;
      backendExecutor = backgroundThreadExecutor;
      promise.onError(__ -> {
        ProgressIndicator indicator = currentIndicator;
        if (indicator != null) {
          indicator.cancel();
        }
      });
      final Expiration expiration = composeExpiration();
      if (expiration != null) {
        final Expiration.Handle expirationHandle = expiration.invokeOnExpiration(promise::cancel);
        promise.onProcessed(value -> expirationHandle.unregisterHandler());
      }
      myExpireCondition = composeCancellationCondition();
    }

    void transferToBgThread() {
      transferToBgThread(ReschedulingAttempt.NULL);
    }

    void transferToBgThread(@NotNull ReschedulingAttempt previousAttempt) {
      backendExecutor.execute(() -> {
        final ProgressIndicator indicator = new EmptyProgressIndicator(creationModality);
        currentIndicator = indicator;
        try {
          ReadAction.run(() -> {
            boolean success = ProgressIndicatorUtils.runWithWriteActionPriority(() -> insideReadAction(previousAttempt, indicator), indicator);
            if (!success && Promises.isPending(promise)) {
              reschedule(previousAttempt);
            }
          });
        }
        finally {
          currentIndicator = null;
        }
      });
    }

    private void reschedule(ReschedulingAttempt previousAttempt) {
      if (!checkObsolete()) {
        doScheduleWithinConstraints(attempt -> dispatchLaterUnconstrained(() -> transferToBgThread(attempt)), previousAttempt);
      }
    }

    void insideReadAction(ReschedulingAttempt previousAttempt, ProgressIndicator indicator) {
      try {
        if (checkObsolete()) {
          return;
        }
        if (!constraintsAreSatisfied()) {
          reschedule(previousAttempt);
          return;
        }

        T result = myComputation.call();

        if (myEdtFinish != null) {
          safeTransferToEdt(result, myEdtFinish, previousAttempt);
        } else {
          promise.setResult(result);
        }
      }
      catch (ProcessCanceledException e) {
        if (!indicator.isCanceled()) {
          promise.setError(e); // don't restart after a manually thrown PCE
        }
        throw e;
      }
      catch (Throwable e) {
        promise.setError(e);
      }
    }

    private boolean constraintsAreSatisfied() {
      return ArraysKt.all(getConstraints(), ContextConstraint::isCorrectContext);
    }

    private boolean checkObsolete() {
      if (Promises.isRejected(promise)) return true;
      if (myExpireCondition != null && myExpireCondition.getAsBoolean()) {
        promise.cancel();
        return true;
      }
      return false;
    }

    void safeTransferToEdt(T result, Pair<? extends ModalityState, ? extends Consumer<T>> edtFinish, ReschedulingAttempt previousAttempt) {
      if (Promises.isRejected(promise)) return;

      long stamp = AsyncExecutionServiceImpl.getWriteActionCounter();

      ApplicationManager.getApplication().invokeLater(() -> {
        if (stamp != AsyncExecutionServiceImpl.getWriteActionCounter()) {
          reschedule(previousAttempt);
          return;
        }

        if (checkObsolete()) {
          return;
        }

        // complete the promise now to prevent write actions inside custom callback from cancelling it
        promise.setResult(result);

        if (promise.isSucceeded()) { // in case another thread managed to cancel it just before `setResult`
          edtFinish.second.accept(result);
        }
      }, edtFinish.first);
    }

  }

  @TestOnly
  public static void cancelAllTasks() {
    while (!ourTasks.isEmpty()) {
      for (CancellablePromise<?> task : ourTasks) {
        task.cancel();
      }
      WriteAction.run(() -> {}); // let background threads complete
    }
  }

  @TestOnly
  public static void waitForAsyncTaskCompletion() {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    for (CancellablePromise<?> task : ourTasks) {
      waitForTask(task);
    }
  }

  @TestOnly
  private static void waitForTask(@NotNull CancellablePromise<?> task) {
    int iteration = 0;
    while (!task.isDone() && iteration++ < 60_000) {
      UIUtil.dispatchAllInvocationEvents();
      try {
        task.blockingGet(1, TimeUnit.MILLISECONDS);
        return;
      }
      catch (TimeoutException ignore) {
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    if (!task.isDone()) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println(ThreadDumper.dumpThreadsToString());
      throw new AssertionError("Too long async task");
    }
  }

}
