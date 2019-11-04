// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.constraints.ExpirableConstrainedExecution;
import com.intellij.openapi.application.constraints.Expiration;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.RunnableCallable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;

import java.util.*;
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
  private final @Nullable ProgressIndicator myProgressIndicator;
  private final Callable<T> myComputation;

  private static final Set<CancellablePromise<?>> ourTasks = ContainerUtil.newConcurrentSet();
  private static final Map<List<Object>, NonBlockingReadActionImpl<?>.Submission> ourTasksByEquality = new HashMap<>();
  private static final AtomicInteger ourUnboundedSubmissionCount = new AtomicInteger();

  NonBlockingReadActionImpl(@NotNull Callable<T> computation) {
    this(computation, null, new ContextConstraint[0], new BooleanSupplier[0], Collections.emptySet(), null, null);
  }

  private NonBlockingReadActionImpl(@NotNull Callable<T> computation,
                                    @Nullable Pair<ModalityState, Consumer<T>> edtFinish,
                                    @NotNull ContextConstraint[] constraints,
                                    @NotNull BooleanSupplier[] cancellationConditions,
                                    @NotNull Set<? extends Expiration> expirationSet,
                                    @Nullable List<Object> coalesceEquality,
                                    @Nullable ProgressIndicator progressIndicator) {
    super(constraints, cancellationConditions, expirationSet);
    myComputation = computation;
    myEdtFinish = edtFinish;
    myCoalesceEquality = coalesceEquality;
    myProgressIndicator = progressIndicator;
  }

  @NotNull
  @Override
  protected NonBlockingReadActionImpl<T> cloneWith(@NotNull ContextConstraint[] constraints,
                                                   @NotNull BooleanSupplier[] cancellationConditions,
                                                   @NotNull Set<? extends Expiration> expirationSet) {
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, constraints, cancellationConditions, expirationSet,
                                           myCoalesceEquality, myProgressIndicator);
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
  public NonBlockingReadAction<T> cancelWith(@NotNull ProgressIndicator progressIndicator) {
    LOG.assertTrue(myProgressIndicator == null, "Unspecified behaviour. Outer progress indicator is already set for the action.");
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, getConstraints(), getCancellationConditions(), getExpirationSet(),
                                           myCoalesceEquality, progressIndicator);
  }

  @Override
  public NonBlockingReadAction<T> finishOnUiThread(@NotNull ModalityState modality, @NotNull Consumer<T> uiThreadAction) {
    return new NonBlockingReadActionImpl<>(myComputation, Pair.create(modality, uiThreadAction),
                                           getConstraints(), getCancellationConditions(), getExpirationSet(), myCoalesceEquality, myProgressIndicator);
  }

  @Override
  public NonBlockingReadAction<T> coalesceBy(@NotNull Object... equality) {
    if (myCoalesceEquality != null) throw new IllegalStateException("Setting equality twice is not allowed");
    if (equality.length == 0) throw new IllegalArgumentException("Equality should include at least one object");
    if (equality.length == 1 && isTooCommon(equality[0])) {
      throw new IllegalArgumentException("Equality should be unique: passing " + equality[0] + " is likely to interfere with unrelated computations from different places");
    }
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, getConstraints(), getCancellationConditions(), getExpirationSet(),
                                           ContainerUtil.newArrayList(equality), myProgressIndicator);
  }

  private static boolean isTooCommon(Object o) {
    return o instanceof Project ||
           o instanceof PsiElement ||
           o instanceof Document ||
           o instanceof VirtualFile ||
           o instanceof Editor ||
           o instanceof FileEditor ||
           o instanceof Class ||
           o instanceof String ||
           o == null;
  }

  @Override
  public T executeSynchronously() throws ProcessCanceledException {
    if (myEdtFinish != null || myCoalesceEquality != null) {
      throw new IllegalStateException(
        (myEdtFinish != null ? "finishOnUiThread" : "coalesceBy") +
        " is not supported with synchronous non-blocking read actions");
    }

    ProgressIndicator outerIndicator = myProgressIndicator != null ? myProgressIndicator
                                                                   : ProgressIndicatorProvider.getGlobalProgressIndicator();
    Executor dummyExecutor = __ -> { throw new UnsupportedOperationException(); };
    return new Submission(new AsyncPromise<>(), dummyExecutor, outerIndicator).executeSynchronously();
  }

  private static void waitForSemaphore(@Nullable ProgressIndicator indicator, @NotNull Semaphore semaphore) {
    while (!semaphore.waitFor(10)) {
      if (indicator != null && indicator.isCanceled()) {
        throw new ProcessCanceledException();
      }
    }
  }

  @Override
  public CancellablePromise<T> submit(@NotNull Executor backgroundThreadExecutor) {
    AsyncPromise<T> promise = new AsyncPromise<>();
    trackSubmission(backgroundThreadExecutor, promise);
    Submission submission = new Submission(promise, backgroundThreadExecutor, myProgressIndicator);
    if (myCoalesceEquality == null) {
      submission.transferToBgThread();
    } else {
      submission.submitOrScheduleCoalesced(myCoalesceEquality);
    }
    return promise;
  }

  private void trackSubmission(@NotNull Executor backgroundThreadExecutor, AsyncPromise<T> promise) {
    if (backgroundThreadExecutor == AppExecutorUtil.getAppExecutorService()) {
      preventTooManySubmissions(promise);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      rememberSubmissionInTests(promise);
    }
  }

  private static void preventTooManySubmissions(AsyncPromise<?> promise) {
    if (ourUnboundedSubmissionCount.incrementAndGet() % 107 == 0) {
      LOG.error("Too many non-blocking read actions submitted at once. " +
                "Please use coalesceBy, BoundedTaskExecutor or another way of limiting the number of concurrently running threads.");
    }
    promise.onProcessed(__ -> ourUnboundedSubmissionCount.decrementAndGet());
  }

  private static void rememberSubmissionInTests(AsyncPromise<?> promise) {
    ourTasks.add(promise);
    promise.onProcessed(__ -> ourTasks.remove(promise));
  }

  private class Submission {
    private final AsyncPromise<T> promise;
    @NotNull private final Executor backendExecutor;
    private volatile ProgressIndicator currentIndicator;
    private final ModalityState creationModality = ModalityState.defaultModalityState();
    @Nullable private final BooleanSupplier myExpireCondition;
    @Nullable private NonBlockingReadActionImpl<?>.Submission myReplacement;
    @Nullable private final ProgressIndicator myProgressIndicator;

    // a sum composed of: 1 for non-done promise, 1 for each currently running thread
    // so 0 means that the process is marked completed or canceled, and it has no running not-yet-finished threads
    private int myUseCount;

    Submission(AsyncPromise<T> promise, @NotNull Executor backgroundThreadExecutor, @Nullable ProgressIndicator outerIndicator) {
      this.promise = promise;
      backendExecutor = backgroundThreadExecutor;
      promise.onError(__ -> {
        ProgressIndicator indicator = currentIndicator;
        if (indicator != null) {
          indicator.cancel();
        }
      });
      if (myCoalesceEquality != null) {
        acquire();
        promise.onProcessed(__ -> release());
      }
      final Expiration expiration = composeExpiration();
      if (expiration != null) {
        final Expiration.Handle expirationHandle = expiration.invokeOnExpiration(promise::cancel);
        promise.onProcessed(value -> expirationHandle.unregisterHandler());
      }
      myExpireCondition = composeCancellationCondition();
      myProgressIndicator = outerIndicator;
    }

    private void acquire() {
      assert myCoalesceEquality != null;
      synchronized (ourTasksByEquality) {
        myUseCount++;
      }
    }

    private void release() {
      assert myCoalesceEquality != null;
      synchronized (ourTasksByEquality) {
        if (--myUseCount == 0 && ourTasksByEquality.get(myCoalesceEquality) == this) {
          scheduleReplacementIfAny();
        }
      }
    }

    private void scheduleReplacementIfAny() {
      if (myReplacement == null || myReplacement.promise.isDone()) {
        ourTasksByEquality.remove(myCoalesceEquality, this);
      } else {
        ourTasksByEquality.put(myCoalesceEquality, myReplacement);
        myReplacement.transferToBgThread();
      }
    }

    void submitOrScheduleCoalesced(@NotNull List<Object> coalesceEquality) {
      synchronized (ourTasksByEquality) {
        if (promise.isDone()) return;

        NonBlockingReadActionImpl<?>.Submission current = ourTasksByEquality.get(coalesceEquality);
        if (current == null) {
          ourTasksByEquality.put(coalesceEquality, this);
          transferToBgThread();
        } else {
          if (!current.getComputationOrigin().equals(getComputationOrigin())) {
            reportCoalescingConflict(current);
          }
          if (current.myReplacement != null) {
            current.myReplacement.promise.cancel();
            assert current == ourTasksByEquality.get(coalesceEquality);
          }
          current.myReplacement = this;
          current.promise.cancel();
        }
      }
    }

    private void reportCoalescingConflict(NonBlockingReadActionImpl<?>.Submission current) {
      LOG.error("Same coalesceBy arguments are already used by " + current.getComputationOrigin() + " so they can cancel each other. " +
                "Please make them more unique.");
    }

    @NotNull
    private String getComputationOrigin() {
      Object computation = myComputation;
      if (computation instanceof RunnableCallable) {
        computation = ((RunnableCallable)computation).getDelegate();
      }
      String name = computation.getClass().getName();
      int dollars = name.indexOf("$$Lambda");
      return dollars >= 0 ? name.substring(0, dollars) : name;
    }

    void transferToBgThread() {
      ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      if (app.isWriteActionInProgress() || app.isWriteActionPending()) {
        rescheduleLater();
        return;
      }

      if (myCoalesceEquality != null) {
        acquire();
      }
      backendExecutor.execute(() -> {
        try {
          if (!attemptComputation()) {
            rescheduleLater();
          }
        }
        finally {
          if (myCoalesceEquality != null) {
            release();
          }
        }
      });
    }

    T executeSynchronously() {
      while (true) {
        attemptComputation();

        if (promise.isCancelled()) {
          throw new ProcessCanceledException();
        }
        if (promise.isDone()) {
          return promise.get();
        }

        Semaphore semaphore = new Semaphore(1);
        dispatchLaterUnconstrained(() -> {
          if (checkObsolete()) {
            semaphore.up();
          } else {
            scheduleWithinConstraints(semaphore::up, null);
          }
        });
        waitForSemaphore(myProgressIndicator, semaphore);
        if (promise.isCancelled()) {
          throw new ProcessCanceledException();
        }
      }
    }

    private boolean attemptComputation() {
      ProgressIndicator indicator = myProgressIndicator != null ? new SensitiveProgressWrapper(myProgressIndicator) {
        @NotNull
        @Override
        public ModalityState getModalityState() {
          return creationModality;
        }
      } : new EmptyProgressIndicator(creationModality);

      currentIndicator = indicator;
      try {
        Ref<ContextConstraint> unsatisfiedConstraint = Ref.create();
        boolean success;
        Runnable runnable = () -> insideReadAction(indicator, unsatisfiedConstraint);
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
          runnable.run();
          success = true;
          if (!unsatisfiedConstraint.isNull()) {
            throw new IllegalStateException("Constraint " + unsatisfiedConstraint + " cannot be satisfied");
          }
        } else {
          success = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(runnable, indicator);
        }
        return success && unsatisfiedConstraint.isNull();
      }
      finally {
        currentIndicator = null;
      }
    }

    private void rescheduleLater() {
      if (Promises.isPending(promise)) {
        dispatchLaterUnconstrained(() -> reschedule());
      }
    }

    private void reschedule() {
      if (!checkObsolete()) {
        scheduleWithinConstraints(() -> transferToBgThread(), null);
      }
    }

    private void insideReadAction(ProgressIndicator indicator, Ref<ContextConstraint> outUnsatisfiedConstraint) {
      try {
        if (checkObsolete()) {
          return;
        }
        ContextConstraint constraint = ContainerUtil.find(getConstraints(), t -> !t.isCorrectContext());
        if (constraint != null) {
          outUnsatisfiedConstraint.set(constraint);
          return;
        }

        T result = myComputation.call();

        if (myEdtFinish != null) {
          safeTransferToEdt(result, myEdtFinish);
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

    private boolean checkObsolete() {
      if (Promises.isRejected(promise)) return true;
      if (myExpireCondition != null && myExpireCondition.getAsBoolean()) {
        promise.cancel();
        return true;
      }
      if (myProgressIndicator != null && myProgressIndicator.isCanceled()) {
        promise.cancel();
        return true;
      }
      return false;
    }

    private void safeTransferToEdt(T result, Pair<? extends ModalityState, ? extends Consumer<T>> edtFinish) {
      if (Promises.isRejected(promise)) return;

      long stamp = AsyncExecutionServiceImpl.getWriteActionCounter();

      ApplicationManager.getApplication().invokeLater(() -> {
        if (stamp != AsyncExecutionServiceImpl.getWriteActionCounter()) {
          reschedule();
          return;
        }

        if (checkObsolete()) {
          return;
        }

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

  @TestOnly
  static Map<List<Object>, NonBlockingReadActionImpl<?>.Submission> getTasksByEquality() {
    return ourTasksByEquality;
  }
}
