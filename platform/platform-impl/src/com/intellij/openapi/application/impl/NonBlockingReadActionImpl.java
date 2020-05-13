// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.startup.ServiceNotReadyException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.NonBlockingReadAction;
import com.intellij.openapi.application.constraints.BaseConstrainedExecution;
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.RunnableCallable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import kotlin.reflect.KClass;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * @author peter
 */
@VisibleForTesting
public class NonBlockingReadActionImpl<T> implements NonBlockingReadAction<T> {
  private static final Logger LOG = Logger.getInstance(NonBlockingReadActionImpl.class);
  private static final Executor SYNC_DUMMY_EXECUTOR = __ -> { throw new UnsupportedOperationException(); };

  private final @Nullable Pair<ModalityState, Consumer<T>> myEdtFinish;
  private final ContextConstraint @NotNull [] myConstraints;
  private final BooleanSupplier @NotNull [] myCancellationConditions;
  private final Set<? extends Disposable> myDisposables;
  private final @Nullable List<Object> myCoalesceEquality;
  private final @Nullable ProgressIndicator myProgressIndicator;
  private final Callable<T> myComputation;

  private static final Set<NonBlockingReadActionImpl<?>.Submission> ourTasks = ContainerUtil.newConcurrentSet();
  private static final Map<List<Object>, NonBlockingReadActionImpl<?>.Submission> ourTasksByEquality = new HashMap<>();
  private static final SubmissionTracker ourUnboundedSubmissionTracker = new SubmissionTracker();

  NonBlockingReadActionImpl(@NotNull Callable<T> computation) {
    this(computation, null, new ContextConstraint[0], new BooleanSupplier[0], Collections.emptySet(), null, null);
  }

  private NonBlockingReadActionImpl(@NotNull Callable<T> computation,
                                    @Nullable Pair<ModalityState, Consumer<T>> edtFinish,
                                    ContextConstraint @NotNull [] constraints,
                                    BooleanSupplier @NotNull [] cancellationConditions,
                                    @NotNull Set<? extends Disposable> disposables,
                                    @Nullable List<Object> coalesceEquality,
                                    @Nullable ProgressIndicator progressIndicator) {
    myComputation = computation;
    myEdtFinish = edtFinish;
    myConstraints = constraints;
    myCancellationConditions = cancellationConditions;
    myDisposables = disposables;
    myCoalesceEquality = coalesceEquality;
    myProgressIndicator = progressIndicator;
  }

  private NonBlockingReadActionImpl<T> withConstraint(ContextConstraint constraint) {
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, ArrayUtil.append(myConstraints, constraint),
                                           myCancellationConditions, myDisposables,
                                           myCoalesceEquality, myProgressIndicator);
  }

  private static void invokeLater(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().invokeLaterOnWriteThread(runnable, ModalityState.any());
  }

  @Override
  public NonBlockingReadAction<T> inSmartMode(@NotNull Project project) {
    return withConstraint(new InSmartMode(project)).expireWithRWCompliantParent(project);
  }

  @Override
  public NonBlockingReadAction<T> withDocumentsCommitted(@NotNull Project project) {
    return withConstraint(new WithDocumentsCommitted(project, ModalityState.any())).expireWithRWCompliantParent(project);
  }

  @Override
  public NonBlockingReadAction<T> expireWhen(@NotNull BooleanSupplier expireCondition) {
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, myConstraints,
                                           ArrayUtil.append(myCancellationConditions, expireCondition),
                                           myDisposables, myCoalesceEquality, myProgressIndicator);
  }

  @NotNull
  @Override
  public NonBlockingReadAction<T> expireWith(@NotNull Disposable parentDisposable) {
    if (parentDisposable instanceof ComponentManager) {
      return expireWithRWCompliantParent((ComponentManager)parentDisposable);
    }
    Set<Disposable> disposables = new HashSet<>();
    disposables.add(parentDisposable);
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, myConstraints, myCancellationConditions, disposables,
                                           myCoalesceEquality, myProgressIndicator);
  }

  /**
   * App/projects/modules are always disposed in a write action,
   * so checking them at computation/finish start is enough
   * and allows to avoid querying Disposer, which isn't free.
   */
  @NotNull
  private NonBlockingReadAction<T> expireWithRWCompliantParent(@NotNull ComponentManager parent) {
    return expireWhen(() -> parent.isDisposed());
  }

  @Override
  public NonBlockingReadAction<T> wrapProgress(@NotNull ProgressIndicator progressIndicator) {
    LOG.assertTrue(myProgressIndicator == null, "Unspecified behaviour. Outer progress indicator is already set for the action.");
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, myConstraints, myCancellationConditions, myDisposables,
                                           myCoalesceEquality, progressIndicator);
  }

  @Override
  public NonBlockingReadAction<T> finishOnUiThread(@NotNull ModalityState modality, @NotNull Consumer<T> uiThreadAction) {
    return new NonBlockingReadActionImpl<>(myComputation, Pair.create(modality, uiThreadAction),
                                           myConstraints, myCancellationConditions, myDisposables, myCoalesceEquality, myProgressIndicator);
  }

  @Override
  public NonBlockingReadAction<T> coalesceBy(Object @NotNull ... equality) {
    if (myCoalesceEquality != null) throw new IllegalStateException("Setting equality twice is not allowed");
    if (equality.length == 0) throw new IllegalArgumentException("Equality should include at least one object");
    if (equality.length == 1 && isTooCommon(equality[0])) {
      throw new IllegalArgumentException("Equality should be unique: passing " + equality[0] + " is likely to interfere with unrelated computations from different places");
    }
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, myConstraints, myCancellationConditions, myDisposables,
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
           o instanceof KClass ||
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
    return new Submission(SYNC_DUMMY_EXECUTOR, outerIndicator).executeSynchronously();
  }

  @Override
  public CancellablePromise<T> submit(@NotNull Executor backgroundThreadExecutor) {
    Submission submission = new Submission(backgroundThreadExecutor, myProgressIndicator);
    if (myCoalesceEquality == null) {
      submission.transferToBgThread();
    } else {
      submission.submitOrScheduleCoalesced(myCoalesceEquality);
    }
    return submission;
  }

  private class Submission extends AsyncPromise<T> {
    @NotNull private final Executor backendExecutor;
    private @Nullable final String myStartTrace;
    private volatile ProgressIndicator currentIndicator;
    private final ModalityState creationModality = ModalityState.defaultModalityState();
    @Nullable private NonBlockingReadActionImpl<?>.Submission myReplacement;
    @Nullable private final ProgressIndicator myProgressIndicator;

    // a sum composed of: 1 for non-done promise, 1 for each currently running thread
    // so 0 means that the process is marked completed or canceled, and it has no running not-yet-finished threads
    private int myUseCount;

    private final AtomicBoolean myCleaned = new AtomicBoolean();
    private final List<Disposable> myExpirationDisposables = new ArrayList<>();

    Submission(@NotNull Executor backgroundThreadExecutor, @Nullable ProgressIndicator outerIndicator) {
      backendExecutor = backgroundThreadExecutor;
      if (myCoalesceEquality != null) {
        acquire();
      }
      myProgressIndicator = outerIndicator;

      if (LOG.isTraceEnabled()) {
        LOG.trace("Creating " + this);
      }

      myStartTrace = hasUnboundedExecutor() ? ourUnboundedSubmissionTracker.preventTooManySubmissions() : null;
      if (shouldTrackInTests()) {
        ourTasks.add(this);
      }
      for (Disposable parent : myDisposables) {
        if (Disposer.isDisposed(parent)) {
          cancel();
          break;
        }
        Disposable child = new Disposable() { // not a lambda to create a separate object for each parent
          @Override
          public void dispose() {
            cancel();
          }
        };
        Disposer.register(parent, child);
        myExpirationDisposables.add(child);
      }
    }

    private boolean shouldTrackInTests() {
      return backendExecutor != SYNC_DUMMY_EXECUTOR && ApplicationManager.getApplication().isUnitTestMode();
    }

    private boolean hasUnboundedExecutor() {
      return backendExecutor == AppExecutorUtil.getAppExecutorService();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      boolean result = super.cancel(mayInterruptIfRunning);
      cleanupIfNeeded();
      return result;
    }

    @Override
    public void setResult(@Nullable T t) {
      super.setResult(t);
      cleanupIfNeeded();
    }

    @Override
    public boolean setError(@NotNull Throwable error) {
      boolean result = super.setError(error);
      cleanupIfNeeded();
      return result;
    }

    @Override
    protected boolean shouldLogErrors() {
      return backendExecutor != SYNC_DUMMY_EXECUTOR;
    }

    private void cleanupIfNeeded() {
      if (myCleaned.compareAndSet(false, true)) {
        cleanup();
      }
    }

    private void cleanup() {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Cleaning " + this);
      }
      ProgressIndicator indicator = currentIndicator;
      if (indicator != null) {
        indicator.cancel();
      }
      if (myCoalesceEquality != null) {
        release();
      }
      for (Disposable disposable : myExpirationDisposables) {
        Disposer.dispose(disposable);
      }
      if (hasUnboundedExecutor()) {
        ourUnboundedSubmissionTracker.unregisterSubmission(myStartTrace);
      }
      if (shouldTrackInTests()) {
        ourTasks.remove(this);
      }
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
      if (myReplacement == null || myReplacement.isDone()) {
        ourTasksByEquality.remove(myCoalesceEquality, this);
      } else {
        ourTasksByEquality.put(myCoalesceEquality, myReplacement);
        myReplacement.transferToBgThread();
      }
    }

    void submitOrScheduleCoalesced(@NotNull List<Object> coalesceEquality) {
      synchronized (ourTasksByEquality) {
        if (isDone()) return;

        NonBlockingReadActionImpl<?>.Submission current = ourTasksByEquality.get(coalesceEquality);
        if (current == null) {
          ourTasksByEquality.put(coalesceEquality, this);
          transferToBgThread();
        } else {
          if (!current.getComputationOrigin().equals(getComputationOrigin())) {
            reportCoalescingConflict(current);
          }
          if (current.myReplacement != null) {
            current.myReplacement.cancel();
            assert current == ourTasksByEquality.get(coalesceEquality);
          }
          current.myReplacement = this;
          current.cancel();
        }
      }
    }

    private void reportCoalescingConflict(NonBlockingReadActionImpl<?>.Submission current) {
      ourTasks.remove(this); // the next line will throw in tests and leave this submission hanging forever
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
      if (LOG.isTraceEnabled()) {
        LOG.trace("Submitting " + this);
      }
      ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      if (app.isWriteActionInProgress() || app.isWriteActionPending()) {
        rescheduleLater();
        return;
      }

      if (myCoalesceEquality != null) {
        acquire();
      }
      backendExecutor.execute(ClientId.decorateRunnable(() -> {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Running in background " + this);
        }
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
      }));
    }

    T executeSynchronously() {
      while (true) {
        attemptComputation();

        if (isCancelled()) {
          throw new ProcessCanceledException();
        }
        if (isDone()) {
          try {
            return blockingGet(0, TimeUnit.MILLISECONDS);
          }
          catch (TimeoutException e) {
            throw new RuntimeException(e);
          }
        }

        Semaphore semaphore = new Semaphore(1);
        invokeLater(() -> {
          if (checkObsolete()) {
            semaphore.up();
          } else {
            BaseConstrainedExecution.scheduleWithinConstraints(semaphore::up, null, myConstraints);
          }
        });
        ProgressIndicatorUtils.awaitWithCheckCanceled(semaphore, myProgressIndicator);
        if (isCancelled()) {
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
      if (myProgressIndicator != null) {
        indicator.setIndeterminate(myProgressIndicator.isIndeterminate());
      }

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
      if (Promises.isPending(this)) {
        invokeLater(() -> reschedule());
      }
    }

    private void reschedule() {
      if (!checkObsolete()) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Rescheduling " + this);
        }
        BaseConstrainedExecution.scheduleWithinConstraints(() -> transferToBgThread(), null, myConstraints);
      }
    }

    private void insideReadAction(ProgressIndicator indicator, Ref<ContextConstraint> outUnsatisfiedConstraint) {
      try {
        if (checkObsolete()) {
          return;
        }
        ContextConstraint constraint = ContainerUtil.find(myConstraints, t -> !t.isCorrectContext());
        if (constraint != null) {
          outUnsatisfiedConstraint.set(constraint);
          return;
        }

        T result = myComputation.call();

        if (myEdtFinish != null) {
          safeTransferToEdt(result, myEdtFinish);
        } else {
          setResult(result);
        }
      }
      catch (ServiceNotReadyException e) {
        throw e;
      }
      catch (ProcessCanceledException e) {
        if (!indicator.isCanceled()) {
          setError(e); // don't restart after a manually thrown PCE
        }
        throw e;
      }
      catch (Throwable e) {
        setError(e);
      }
    }

    private boolean checkObsolete() {
      if (Promises.isRejected(this)) return true;
      for (BooleanSupplier condition : myCancellationConditions) {
        if (condition.getAsBoolean()) {
          cancel();
          return true;
        }
      }
      if (myProgressIndicator != null && myProgressIndicator.isCanceled()) {
        cancel();
        return true;
      }
      return false;
    }

    private void safeTransferToEdt(T result, Pair<? extends ModalityState, ? extends Consumer<T>> edtFinish) {
      if (Promises.isRejected(this)) return;

      long stamp = AsyncExecutionServiceImpl.getWriteActionCounter();

      ApplicationManager.getApplication().invokeLater(() -> {
        if (stamp != AsyncExecutionServiceImpl.getWriteActionCounter()) {
          reschedule();
          return;
        }

        if (checkObsolete()) {
          return;
        }

        setResult(result);

        if (isSucceeded()) { // in case another thread managed to cancel it just before `setResult`
          edtFinish.second.accept(result);
        }
      }, edtFinish.first);
    }

    @Override
    public String toString() {
      return "Submission{" + myComputation + ", " + getState() + "}";
    }
  }

  @TestOnly
  public static void waitForAsyncTaskCompletion() {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    for (NonBlockingReadActionImpl<?>.Submission task : ourTasks) {
      waitForTask(task);
    }
  }

  @TestOnly
  private static void waitForTask(@NotNull NonBlockingReadActionImpl<?>.Submission task) {
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
      throw new AssertionError("Too long async task " + task);
    }
  }

  @TestOnly
  static Map<List<Object>, NonBlockingReadActionImpl<?>.Submission> getTasksByEquality() {
    return ourTasksByEquality;
  }
}
