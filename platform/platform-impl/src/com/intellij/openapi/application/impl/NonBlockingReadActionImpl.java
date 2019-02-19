// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.NonBlockingReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.async.ConstrainedExecution.ContextConstraint;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;

import java.util.List;
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
public class NonBlockingReadActionImpl<T> implements NonBlockingReadAction<T> {
  private final @Nullable Pair<ModalityState, Consumer<T>> myEdtFinish;
  private final List<ContextConstraint> myConstraints;
  private final BooleanSupplier myExpireCondition;
  private final Callable<T> myComputation;

  private static final Set<CancellablePromise<?>> ourTasks = ContainerUtil.newConcurrentSet();

  NonBlockingReadActionImpl(@Nullable Pair<ModalityState, Consumer<T>> edtFinish,
                            @NotNull List<ContextConstraint> constraints,
                            @NotNull BooleanSupplier expireCondition,
                            @NotNull Callable<T> computation) {
    myEdtFinish = edtFinish;
    myConstraints = constraints;
    myExpireCondition = expireCondition;
    myComputation = computation;
  }

  @Override
  public NonBlockingReadAction<T> inSmartMode(@NotNull Project project) {
    return new NonBlockingReadActionImpl<>(myEdtFinish, ContainerUtil.append(myConstraints, new InSmartMode(project)), myExpireCondition, myComputation).expireWhen(project::isDisposed);
  }

  @Override
  public NonBlockingReadAction<T> withDocumentsCommitted(@NotNull Project project) {
    return new NonBlockingReadActionImpl<>(myEdtFinish, ContainerUtil.append(myConstraints, new WithDocumentsCommitted(project, ModalityState.any())), myExpireCondition, myComputation).expireWhen(project::isDisposed);
  }

  @Override
  public NonBlockingReadAction<T> expireWhen(@NotNull BooleanSupplier expireCondition) {
    return new NonBlockingReadActionImpl<>(myEdtFinish, myConstraints, () -> myExpireCondition.getAsBoolean() || expireCondition.getAsBoolean(), myComputation);
  }

  @Override
  public NonBlockingReadAction<T> finishOnUiThread(@NotNull ModalityState modality, @NotNull Consumer<T> uiThreadAction) {
    return new NonBlockingReadActionImpl<>(Pair.create(modality, uiThreadAction), myConstraints, myExpireCondition, myComputation);
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

    Submission(AsyncPromise<? super T> promise, @NotNull Executor backgroundThreadExecutor) {
      this.promise = promise;
      backendExecutor = backgroundThreadExecutor;
      promise.onError(__ -> {
        ProgressIndicator indicator = currentIndicator;
        if (indicator != null) {
          indicator.cancel();
        }
      });
    }

    void transferToBgThread() {
      backendExecutor.execute(() -> {
        try {
          ProgressIndicator indicator = new EmptyProgressIndicator(creationModality);
          currentIndicator = indicator;
          ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> insideReadAction(indicator), indicator);
        }
        finally {
          currentIndicator = null;
        }

        if (Promises.isPending(promise)) {
          rescheduleLater();
        }
      });
    }

    private void rescheduleLater() {
      for (ContextConstraint constraint : myConstraints) {
        if (!constraint.isCorrectContext()) {
          constraint.schedule(this::transferToBgThread);
          return;
        }
      }
      ApplicationManager.getApplication().invokeLater(this::transferToBgThread, ModalityState.any());
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
      return ContainerUtil.all(myConstraints, ContextConstraint::isCorrectContext);
    }

    private boolean checkObsolete() {
      if (myExpireCondition.getAsBoolean()) {
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
