// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.ContextAwareRunnable;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.concurrency.ThreadContext;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.startup.ServiceNotReadyException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.NonBlockingReadAction;
import com.intellij.openapi.application.constraints.BaseConstrainedExecution;
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
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
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.RunnableCallable;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import io.opentelemetry.api.metrics.Meter;
import kotlin.Result;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.reflect.KClass;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.EDT;
import static com.intellij.util.SystemProperties.getBooleanProperty;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@VisibleForTesting
public final class NonBlockingReadActionImpl<T> implements NonBlockingReadAction<T> {
  private static final Logger LOG = Logger.getInstance(NonBlockingReadActionImpl.class);
  private static final Executor SYNC_DUMMY_EXECUTOR = __ -> {
    throw new UnsupportedOperationException();
  };

  // myModalityState and myUiThreadAction must be both null or both not-null
  private final ModalityState myModalityState;
  private final Consumer<? super T> myUiThreadAction;
  private final ContextConstraint @NotNull [] myConstraints;
  private final BooleanSupplier @NotNull [] myCancellationConditions;
  private final Set<? extends Disposable> myDisposables;
  private final @Nullable List<?> myCoalesceEquality;
  private final @Nullable ProgressIndicator myProgressIndicator;
  /** Original computation passed in */
  private final Callable<? extends T> myOriginalComputation;
  /** Computation to be executed -- possible, wrapped into some monitoring */
  private final Callable<? extends T> myActualComputation;

  private static final Set<Submission<?>> ourTasks = ConcurrentCollectionFactory.createConcurrentSet();
  private static final Map<List<?>, Submission<?>> ourTasksByEquality = new HashMap<>();
  private static final SubmissionTracker ourUnboundedSubmissionTracker = new SubmissionTracker();

  /* ======================== monitoring: ================================================= */
  private static final boolean ENABLE_OTEL_MONITORING = getBooleanProperty("idea.non-blocking-action.enable-monitoring", true);
  private static final @Nullable OTelMonitor MONITOR;

  static {
    LOG.info("OTel monitoring for NonBlockingReadAction is " + (ENABLE_OTEL_MONITORING ? "enabled" : "disabled"));
    if (ENABLE_OTEL_MONITORING) {
      Meter meter = TelemetryManager.getInstance().getMeter(EDT);
      MONITOR = new OTelMonitor(meter);
    }
    else {
      MONITOR = null;
    }
  }

  /* ======================== monitoring end =============================================== */

  NonBlockingReadActionImpl(@NotNull Callable<? extends T> computation) {
    this(computation, null, null, new ContextConstraint[0], new BooleanSupplier[0], Collections.emptySet(), null, null);
  }

  private NonBlockingReadActionImpl(@NotNull Callable<? extends T> computation,
                                    @Nullable ModalityState modalityState,
                                    @Nullable Consumer<? super T> uiThreadAction,
                                    ContextConstraint @NotNull [] constraints,
                                    BooleanSupplier @NotNull [] cancellationConditions,
                                    @NotNull Set<? extends Disposable> disposables,
                                    @Nullable List<?> coalesceEquality,
                                    @Nullable ProgressIndicator progressIndicator) {
    myOriginalComputation = computation;
    myActualComputation = MONITOR == null ? computation :
                          MONITOR.wrap(computation);
    myModalityState = modalityState;
    myUiThreadAction = uiThreadAction;
    myConstraints = constraints;
    myCancellationConditions = cancellationConditions;
    myDisposables = disposables;
    myCoalesceEquality = coalesceEquality;
    myProgressIndicator = progressIndicator;
    if ((modalityState == null) != (uiThreadAction == null)) {
      throw new IllegalArgumentException(
        "myModalityState and myUiThreadAction must be both null or both not-null but got: " + modalityState + ", " + uiThreadAction);
    }
  }

  private @NotNull NonBlockingReadActionImpl<T> withConstraint(@NotNull ContextConstraint constraint) {
    return new NonBlockingReadActionImpl<>(myOriginalComputation, myModalityState, myUiThreadAction, ArrayUtil.append(myConstraints, constraint),
                                           myCancellationConditions, myDisposables,
                                           myCoalesceEquality, myProgressIndicator);
  }

  private static void invokeLater(@NotNull Runnable runnable) {
    ApplicationManager.getApplication()
      .invokeLaterOnWriteThread(runnable, ModalityState.any(), ApplicationManager.getApplication().getDisposed());
  }

  @Override
  public @NotNull NonBlockingReadAction<T> inSmartMode(@NotNull Project project) {
    return withConstraint(new InSmartMode(project)).expireWith(project);
  }

  @Override
  public @NotNull NonBlockingReadAction<T> withDocumentsCommitted(@NotNull Project project) {
    return withConstraint(new WithDocumentsCommitted(project, ModalityState.any())).expireWith(project);
  }

  @Override
  public @NotNull NonBlockingReadAction<T> expireWhen(@NotNull BooleanSupplier expireCondition) {
    return new NonBlockingReadActionImpl<>(myOriginalComputation, myModalityState, myUiThreadAction, myConstraints,
                                           ArrayUtil.append(myCancellationConditions, expireCondition),
                                           myDisposables, myCoalesceEquality, myProgressIndicator);
  }

  @Override
  public @NotNull NonBlockingReadAction<T> expireWith(@NotNull Disposable parentDisposable) {
    Set<Disposable> disposables = new HashSet<>(myDisposables);
    disposables.add(parentDisposable);
    return new NonBlockingReadActionImpl<>(myOriginalComputation, myModalityState, myUiThreadAction, myConstraints, myCancellationConditions,
                                           disposables,
                                           myCoalesceEquality, myProgressIndicator);
  }

  @Override
  public @NotNull NonBlockingReadAction<T> wrapProgress(@NotNull ProgressIndicator progressIndicator) {
    LOG.assertTrue(myProgressIndicator == null, "Unspecified behaviour. Outer progress indicator is already set for the action.");
    return new NonBlockingReadActionImpl<>(myOriginalComputation, myModalityState, myUiThreadAction, myConstraints, myCancellationConditions,
                                           myDisposables,
                                           myCoalesceEquality, progressIndicator);
  }

  @Override
  public @NotNull NonBlockingReadAction<T> finishOnUiThread(@NotNull ModalityState modality, @NotNull Consumer<? super T> uiThreadAction) {
    return new NonBlockingReadActionImpl<>(myOriginalComputation, modality, uiThreadAction,
                                           myConstraints, myCancellationConditions, myDisposables, myCoalesceEquality, myProgressIndicator);
  }

  @Override
  public @NotNull NonBlockingReadAction<T> coalesceBy(Object @NotNull ... equality) {
    if (myCoalesceEquality != null) throw new IllegalStateException("Setting equality twice is not allowed");
    if (equality.length == 0) throw new IllegalArgumentException("Equality should include at least one object");
    if (equality.length == 1 && isTooCommon(equality[0])) {
      throw new IllegalArgumentException(
        "Equality should be unique: passing " + equality[0] + " is likely to interfere with unrelated computations from different places");
    }
    return new NonBlockingReadActionImpl<>(myOriginalComputation, myModalityState, myUiThreadAction, myConstraints, myCancellationConditions,
                                           myDisposables,
                                           new ArrayList<>(Arrays.asList(equality)), myProgressIndicator);
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
    if (myModalityState != null || myCoalesceEquality != null) {
      throw new IllegalStateException(
        (myModalityState != null ? "finishOnUiThread" : "coalesceBy") +
        " is not supported with synchronous non-blocking read actions");
    }

    ProgressIndicator outerIndicator = myProgressIndicator != null ? myProgressIndicator
                                                                   : ProgressIndicatorProvider.getGlobalProgressIndicator();
    return new Submission<>(this, SYNC_DUMMY_EXECUTOR, outerIndicator).executeSynchronously();
  }

  @SuppressWarnings("unused")
  private void schedule(@Async.Schedule Callable<? extends T> computation) {
    // dummy method to capture the original computation object, see org.jetbrains.annotations.Async.Schedule
  }

  @Override
  public @NotNull CancellablePromise<T> submit(@NotNull Executor backgroundThreadExecutor) {
    schedule(myOriginalComputation);
    Submission<T> submission = new Submission<>(this, backgroundThreadExecutor, myProgressIndicator);
    if (myCoalesceEquality == null) {
      submission.transferToBgThread();
    }
    else {
      submission.submitOrScheduleCoalesced(myCoalesceEquality);
    }
    return submission;
  }

  private static final class Submission<T> extends AsyncPromise<T> {
    private final @NotNull Executor backendExecutor;
    private final @Nullable String myStartTrace;
    private volatile ProgressIndicator currentIndicator;
    private final ModalityState creationModality = ModalityState.defaultModalityState();
    private @Nullable Submission<?> myReplacement;
    private final @Nullable ProgressIndicator myProgressIndicator;
    private final @NotNull NonBlockingReadActionImpl<T> builder;
    private final @NotNull ChildContext myChildContext;
    private final @NotNull AccessToken childContextToken;

    // a sum composed of: 1 for non-done promise, 1 for each currently running thread,
    // so 0 means that the process is marked completed or canceled, and it has no running not-yet-finished threads
    private int myUseCount;

    private final AtomicBoolean myCleaned = new AtomicBoolean();
    private final List<Disposable> myExpirationDisposables = new ArrayList<>();

    Submission(@NotNull NonBlockingReadActionImpl<T> builder,
               @NotNull Executor backgroundThreadExecutor,
               @Nullable ProgressIndicator outerIndicator) {
      backendExecutor = backgroundThreadExecutor;
      this.builder = builder;
      myChildContext = Propagation.createChildContext("NonBlockingReadActionImpl.Submission: " + this);
      childContextToken = myChildContext.applyContextActions(false);
      if (builder.myCoalesceEquality != null) {
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
      if (!builder.myDisposables.isEmpty()) {
        ApplicationManager.getApplication().runReadAction(() -> expireWithDisposables(this.builder.myDisposables));
      }
    }

    private void expireWithDisposables(@NotNull Set<? extends Disposable> disposables) {
      for (Disposable parent : disposables) {
        if (parent instanceof Project ? ((Project)parent).isDisposed() : Disposer.isDisposed(parent)) {
          cancel();
          break;
        }
        Disposable child = new CheckedDisposable() {
          private volatile boolean disposed;
          @Override
          public boolean isDisposed() {
            return disposed;
          }

          @Override
          public void dispose() {
            disposed = true;
            // NB: We call here `super.cancel()` directly instead of `cancel()`
            // The reason is that `Job` is needed to cover the scheduling of `myUiThreadAction`,
            // so its lifetime is bigger than the lifetime of computation in NBRA, hence `Job` should not be cancelled in `dispose`.
            Submission.super.cancel();
          }
        };
        //noinspection TestOnlyProblems
        Disposable parentDisposable =
          parent instanceof ProjectImpl && ((ProjectEx)parent).isLight() ? ((ProjectImpl)parent).getEarlyDisposable() : parent;
        if (!Disposer.tryRegister(parentDisposable, child)) {
          cancel();
          break;
        }
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
      // There are two ways to appear in this method:
      // 1. As a result of external disposal (for example, when this NBRA is bound to a toolwindow, and the window is ready to close),
      // 2. And during `setResult` -> `cleanupIfNeeded` -> `myExpirationDisposables.dispose` -> `AsyncPromise.cancel`
      // We need to abort the job only in the first case, but not in the second one.
      // Because in the case of `setResult` there can be a UI callback, and we need to cancel Job strictly after the callback finishes.
      if (!isSucceeded()) {
        // we must not create CancellationException here,
        // because filling the stacktrace causes performance degradation
        cancelJob(null);
      }
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
      if (builder.myCoalesceEquality != null) {
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
      assert builder.myCoalesceEquality != null;
      synchronized (ourTasksByEquality) {
        myUseCount++;
      }
    }

    private void release() {
      assert builder.myCoalesceEquality != null;
      synchronized (ourTasksByEquality) {
        if (--myUseCount == 0 && ourTasksByEquality.get(builder.myCoalesceEquality) == this) {
          scheduleReplacementIfAny();
        }
      }
    }

    private void scheduleReplacementIfAny() {
      if (myReplacement == null || myReplacement.isDone()) {
        ourTasksByEquality.remove(builder.myCoalesceEquality, this);
      }
      else {
        ourTasksByEquality.put(builder.myCoalesceEquality, myReplacement);
        myReplacement.transferToBgThread();
      }
    }

    void submitOrScheduleCoalesced(@NotNull List<?> coalesceEquality) {
      synchronized (ourTasksByEquality) {
        if (isDone()) return;

        Submission<?> current = ourTasksByEquality.putIfAbsent(coalesceEquality, this);
        if (current == null) {
          transferToBgThread();
        }
        else {
          if (!current.getComputationOrigin().equals(getComputationOrigin())) {
            //RC: Sort of fool-proofing: sometimes it _could_ be OK to invoke 2 ReadActions with different .computable
            //    from different places in code, but with same .coalesceBy -- but it is much more likely an error.
            //    Hence, we prefer to prohibit it completely:
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

    private void reportCoalescingConflict(@NotNull Submission<?> current) {
      ourTasks.remove(this); // the next line will throw in tests and leave this submission hanging forever
      LOG.error("Same coalesceBy arguments are already used by " + current.getComputationOrigin() + " so they can cancel each other. " +
                "Please make them more unique.");
    }

    private @NotNull String getComputationOrigin() {
      Object computation = builder.myOriginalComputation;
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
      if (app.isWriteActionInProgress() || app.isWriteActionPending() ||
          app.isReadAccessAllowed() && builder.findUnsatisfiedConstraint() != null) {
        rescheduleLater();
        return;
      }

      if (builder.myCoalesceEquality != null) {
        acquire();
      }
      try {
        Runnable r = ClientId.decorateRunnable(() -> {
          if (LOG.isTraceEnabled()) {
            LOG.trace("Running in background " + this);
          }
          try {
            boolean computationSuccessful;
            if (AppExecutorUtil.propagateContext()) {
              try (AccessToken ignored = ThreadContext.installThreadContext(myChildContext.getContext(), true)) {
                computationSuccessful = attemptComputation();
              }
            } else {
              computationSuccessful = attemptComputation();
            }
            if (!computationSuccessful) {
              rescheduleLater();
            }
          }
          finally {
            if (builder.myCoalesceEquality != null) {
              release();
            }
          }
        });
        backendExecutor.execute((ContextAwareRunnable)() -> r.run());
      }
      catch (RejectedExecutionException e) {
        LOG.warn("Rejected: " + this);
        throw e;
      }
    }

    T executeSynchronously() {
      try {
        while (true) {
          attemptComputation();

          if (isDone()) {
            if (isCancelled()) {
              throw new ProcessCanceledException();
            }
            try {
              return blockingGet(0, TimeUnit.MILLISECONDS);
            }
            catch (TimeoutException e) {
              throw new RuntimeException(e);
            }
          }

          ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(myProgressIndicator);
          ContextConstraint[] constraints = builder.myConstraints;
          if (shouldFinishOnEdt() || constraints.length != 0) {
            Semaphore semaphore = new Semaphore(1);
            invokeLater((ContextAwareRunnable) () -> {
              if (checkObsolete()) {
                semaphore.up();
              }
              else {
                BaseConstrainedExecution.scheduleWithinConstraints(semaphore::up, null, constraints);
              }
            });
            ProgressIndicatorUtils.awaitWithCheckCanceled(semaphore, myProgressIndicator);
            if (isCancelled()) {
              throw new ProcessCanceledException();
            }
          }
        }
      } catch (ProcessCanceledException e) {
        cancelJob(e);
        throw e;
      }
      finally {
        cleanupIfNeeded();
      }
    }

    private boolean attemptComputation() {
      ProgressIndicator indicator =
        myProgressIndicator == null ? new EmptyProgressIndicator(creationModality) :
        new SensitiveProgressWrapper(myProgressIndicator) {
          @Override
          public @NotNull ModalityState getModalityState() {
            return creationModality;
          }
        };
      if (myProgressIndicator != null) {
        indicator.setIndeterminate(myProgressIndicator.isIndeterminate());
      }

      currentIndicator = indicator;
      try {
        Ref<ContextConstraint> unsatisfiedConstraint = Ref.create();
        boolean success;
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
          insideReadAction(indicator, unsatisfiedConstraint);
          success = true;
          if (!unsatisfiedConstraint.isNull()) {
            throw new IllegalStateException("Constraint " + unsatisfiedConstraint + " cannot be satisfied");
          }
        }
        else {
          if (myProgressIndicator != null) {
            try {
              // Give ProgressSuspender a chance to suspend now. It can't do it under a read-action
              myProgressIndicator.checkCanceled();
            }
            catch (ProcessCanceledException e) {
              return false;
            }
          }
          success = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> insideReadAction(indicator, unsatisfiedConstraint),
                                                                                  indicator);
        }
        return success && unsatisfiedConstraint.isNull();
      }
      finally {
        currentIndicator = null;
      }
    }

    private void rescheduleLater() {
      if (Promises.isPending(this)) {
        invokeLater((ContextAwareRunnable) () -> reschedule());
      }
    }

    private void reschedule() {
      if (!checkObsolete()) {
        if (LOG.isTraceEnabled()) {
          LOG.trace("Rescheduling " + this);
        }
        BaseConstrainedExecution.scheduleWithinConstraints(() -> transferToBgThread(), null, builder.myConstraints);
      }
    }

    private void insideReadAction(@NotNull ProgressIndicator indicator, @NotNull Ref<? super ContextConstraint> outUnsatisfiedConstraint) {
      try {
        if (checkObsolete()) {
          cancelJob(null);
          return;
        }
        ContextConstraint constraint = builder.findUnsatisfiedConstraint();
        if (constraint != null) {
          outUnsatisfiedConstraint.set(constraint);
          return;
        }

        T result = builder.myActualComputation.call();

        if (shouldFinishOnEdt()) {
          safeTransferToEdt(result);
        }
        else {
          try {
            setResult(result);
          } finally {
            completeJob();
          }
        }
      }
      catch (ServiceNotReadyException e) {
        failJob(e);
        throw e;
      }
      catch (ProcessCanceledException e) {
        if (!indicator.isCanceled()) {
          failJob(e);
          setError(e); // don't restart after a manually thrown PCE
        }
        throw e;
      }
      catch (Throwable e) {
        failJob(e);
        setError(e);
      }
    }

    @Override
    public void cancel() {
      super.cancel();
      cancelJob(null);
    }

    @Override
    public boolean isCancelled() {
      if (super.isCancelled()) {
        return true;
      }
      Job job = myChildContext.getJob();
      return job != null && job.isCancelled();
    }

    private boolean shouldFinishOnEdt() {
      return builder.myModalityState != null;
    }

    private void cancelJob(@Nullable CancellationException e) {
      Job job = myChildContext.getJob();
      if (job != null) {
        job.cancel(e);
      }
      childContextToken.finish();
    }

    private void completeJob() {
      Continuation<Unit> continuation = myChildContext.getContinuation();
      if (continuation != null) {
        continuation.resumeWith(Unit.INSTANCE);
      }
      childContextToken.finish();
    }

    private void failJob(@NotNull Throwable reason) {
      Continuation<Unit> continuation = myChildContext.getContinuation();
      if (continuation != null) {
        continuation.resumeWith(new Result.Failure(reason));
      }
      childContextToken.finish();
    }

    private boolean checkObsolete() {
      if (Promises.isRejected(this)) {
        cancelJob(new CancellationException("Cancelled by rejection"));
        return true;
      }
      for (BooleanSupplier condition : builder.myCancellationConditions) {
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

    private void safeTransferToEdt(T result) {
      if (Promises.isRejected(this)) {
        cancelJob(null);
        return;
      }

      long stamp = AsyncExecutionServiceImpl.getWriteActionCounter();

      ApplicationManager.getApplication().invokeLater((ContextAwareRunnable) () -> {
        if (stamp != AsyncExecutionServiceImpl.getWriteActionCounter()) {
          reschedule();
          return;
        }

        if (checkObsolete()) {
          cancelJob(null);
          return;
        }

        setResult(result);

        if (isSucceeded()) { // in case when another thread managed to cancel it just before `setResult`
          try {
            if (AppExecutorUtil.propagateContext()) {
              try (AccessToken ignored = ThreadContext.installThreadContext(myChildContext.getContext(), false)) {
                builder.myUiThreadAction.accept(result);
              }
            } else {
              builder.myUiThreadAction.accept(result);
            }
          } finally {
            completeJob();
          }
        }
      }, builder.myModalityState, __ -> isCancelled());
    }

    @Override
    public String toString() {
      return "Submission{" + builder.myOriginalComputation + ", " + getState() + "}";
    }
  }

  private @Nullable ContextConstraint findUnsatisfiedConstraint() {
    return ContainerUtil.find(myConstraints, t -> !t.isCorrectContext());
  }

  /**
   * Waits and pumps UI events until all submitted non-blocking read actions have completed. But only if they have chance to:
   * in dumb mode, submissions with {@link #inSmartMode} are ignored, because dumbness works differently in tests,
   * and a test might never switch to the smart mode at all.
   */
  @TestOnly
  public static void waitForAsyncTaskCompletion() {
    ThreadingAssertions.assertEventDispatchThread();
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    for (Submission<?> task : ourTasks) {
      waitForTask(task);
    }
  }

  @TestOnly
  private static void waitForTask(@NotNull Submission<?> task) {
    ThreadingAssertions.assertEventDispatchThread();
    for (ContextConstraint constraint : task.builder.myConstraints) {
      if (constraint instanceof InSmartMode && !constraint.isCorrectContext()) {
        return;
      }
    }

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
  static @NotNull Map<List<?>, Submission<?>> getTasksByEquality() {
    return ourTasksByEquality;
  }

  /**
   * Encapsulates OTel monitoring fields and methods
   */
  private static final class OTelMonitor implements AutoCloseable {

    /**
     * How many actions were successfully executed until the end of the computation (i.e. return result)
     */
    private final AtomicInteger finalizedExecutionsCount = new AtomicInteger();
    /**
     * How many actions throw exception during execution -- including {@link ProcessCanceledException}
     * leading to retry of the action
     */
    private final AtomicInteger failedExecutionsCount = new AtomicInteger();
    /**
     * Total time (in microseconds) of successful executions -- i.e., those that run to the end
     */
    private final AtomicLong finalizedExecutionTimeUs = new AtomicLong();
    /**
     * Total time (in microseconds) of executions finished with exception -- including {@link ProcessCanceledException}
     * leading to retry of the action
     */
    private final AtomicLong failedExecutionTimeUs = new AtomicLong();
    private final AutoCloseable otelSubscription;

    private OTelMonitor(@NotNull Meter meter) {
      var finalizedExecutionsCounter = meter.counterBuilder("NonBlockingReadAction.finalizedExecutionsCount").buildObserver();
      var failedExecutionsCounter = meter.counterBuilder("NonBlockingReadAction.failedExecutionsCount").buildObserver();
      var finalizedExecutionTimeUsCounter = meter.counterBuilder("NonBlockingReadAction.finalizedExecutionTimeUs").buildObserver();
      var failedExecutionTimeUsCounter = meter.counterBuilder("NonBlockingReadAction.failedExecutionTimeUs").buildObserver();

      otelSubscription = meter.batchCallback(
        () -> {
          finalizedExecutionsCounter.record(finalizedExecutionsCount.longValue());
          finalizedExecutionTimeUsCounter.record(finalizedExecutionTimeUs.longValue());

          failedExecutionsCounter.record(failedExecutionsCount.longValue());
          failedExecutionTimeUsCounter.record(failedExecutionTimeUs.longValue());
        },
        finalizedExecutionsCounter, failedExecutionsCounter,
        finalizedExecutionTimeUsCounter, failedExecutionTimeUsCounter
      );
    }

    @NotNull
    <V> Callable<V> wrap(@NotNull Callable<V> computation) {
      return new MonitoredComputation<>(computation);
    }

    @Contract(pure = true)
    private <V> V callWrapped(@Async.Execute @NotNull Callable<V> computation) throws Exception {
      long startedAtNs = System.nanoTime();
      try {
        V result = computation.call();

        finalizedExecutionsCount.incrementAndGet();
        long finishedAtNs = System.nanoTime();
        long durationUs = NANOSECONDS.toMicros(finishedAtNs - startedAtNs);
        finalizedExecutionTimeUs.addAndGet(durationUs);

        return result;
      }
      catch (Throwable t) {
        failedExecutionsCount.incrementAndGet();
        long finishedAtNs = System.nanoTime();
        long durationUs = NANOSECONDS.toMicros(finishedAtNs - startedAtNs);
        failedExecutionTimeUs.addAndGet(durationUs);
        throw t;
      }
    }

    @Override
    public void close() throws Exception {
      otelSubscription.close();
    }

    private final class MonitoredComputation<V> implements Callable<V> {
      private final Callable<V> wrappedComputation;

      private MonitoredComputation(@NotNull Callable<V> wrappedComputation) {
        this.wrappedComputation = wrappedComputation;
      }

      @Override
      public V call() throws Exception {
        return callWrapped(wrappedComputation);
      }
    }
  }
}
