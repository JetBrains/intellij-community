// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.NonBlockingReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.constraints.ExpirableConstrainedExecution;
import com.intellij.openapi.application.constraints.Expiration;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.Semaphore;
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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * @author peter
 */
@VisibleForTesting
public class NonBlockingReadActionImpl<T>
  extends ExpirableConstrainedExecution<NonBlockingReadActionImpl<T>>
  implements NonBlockingReadAction<T> {

  private final @Nullable Pair<ModalityState, Consumer<T>> myEdtFinish;
  private final Callable<T> myComputation;

  private static final Set<CancellablePromise<?>> ourTasks = ContainerUtil.newConcurrentSet();

  NonBlockingReadActionImpl(@NotNull Callable<T> computation) {
    this(computation, null, new ContextConstraint[0], new BooleanSupplier[0], Collections.emptySet());
  }

  private NonBlockingReadActionImpl(@NotNull Callable<T> computation,
                                    @Nullable Pair<ModalityState, Consumer<T>> edtFinish,
                                    @NotNull ContextConstraint[] constraints,
                                    @NotNull BooleanSupplier[] cancellationConditions,
                                    @NotNull Set<? extends Expiration> expirationSet) {
    super(constraints, cancellationConditions, expirationSet);
    myComputation = computation;
    myEdtFinish = edtFinish;
  }

  @NotNull
  @Override
  protected NonBlockingReadActionImpl<T> cloneWith(@NotNull ContextConstraint[] constraints,
                                                   @NotNull BooleanSupplier[] cancellationConditions,
                                                   @NotNull Set<? extends Expiration> expirationSet) {
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, constraints, cancellationConditions, expirationSet);
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
                                           getConstraints(), getCancellationConditions(), getExpirationSet());
  }

  @Override
  public CancellablePromise<T> submit(@NotNull Executor backgroundThreadExecutor) {
    AsyncPromise<T> promise = new AsyncPromise<>();
    new Submission(promise, backgroundThreadExecutor).transferToBgThread();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ourTasks.add(promise);
      promise.onProcessed(__ -> ourTasks.remove(promise));
    }
    return promise;
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
          ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> insideReadAction(indicator), indicator);
        }
        finally {
          currentIndicator = null;
        }

        if (Promises.isPending(promise)) {
          doScheduleWithinConstraints(attempt -> dispatchLaterUnconstrained(() -> transferToBgThread(attempt)), previousAttempt);
        }
      });
    }

    void insideReadAction(ProgressIndicator indicator) {
      try {
        if (checkObsolete() || !constraintsAreSatisfied()) return;

        T result = myComputation.call();

        if (myEdtFinish != null) {
          safeTransferToEdt(result, myEdtFinish, indicator);
        } else {
          promise.setResult(result);
        }
      }
      catch (Throwable e) {
        if (!indicator.isCanceled()) {
          promise.setError(e);
        }
      }
    }

    private boolean constraintsAreSatisfied() {
      return ArraysKt.all(getConstraints(), ContextConstraint::isCorrectContext);
    }

    private boolean checkObsolete() {
      if (promise.isCancelled()) return true;
      if (myExpireCondition != null && myExpireCondition.getAsBoolean()) {
        promise.cancel();
        return true;
      }
      return false;
    }

    void safeTransferToEdt(T result, Pair<? extends ModalityState, ? extends Consumer<T>> edtFinish, ProgressIndicator indicator) {
      if (Promises.isRejected(promise)) return;

      Semaphore semaphore = new Semaphore(1);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (indicator.isCanceled()) {
          // a write action has managed to sneak in before us, or the whole computation got canceled;
          // anyway, nobody waits for us on bg thread, so we just exit
          return;
        }

        if (checkObsolete()) {
          semaphore.up();
          return;
        }

        // complete the promise now to prevent write actions inside custom callback from cancelling it
        promise.setResult(result);

        // now background thread may release its read lock, and we continue on EDT, invoking custom callback
        semaphore.up();

        if (promise.isSucceeded()) { // in case another thread managed to cancel it just before `setResult`
          edtFinish.second.accept(result);
        }
      }, edtFinish.first);

      // don't release read action until we're on EDT, to avoid result invalidation in between
      while (!semaphore.waitFor(10)) {
        if (indicator.isCanceled()) { // checkCanceled isn't enough, because some smart developers disable it
          throw new ProcessCanceledException();
        }
      }
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
