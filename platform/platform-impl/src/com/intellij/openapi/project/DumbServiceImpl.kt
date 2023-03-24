// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.ui.DeprecationStripePanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

@ApiStatus.Internal
public class DumbServiceImpl extends DumbService implements Disposable, ModificationTracker, DumbServiceBalloon.Service {
  static final ExtensionPointName<StartupActivity.RequiredForSmartMode> REQUIRED_FOR_SMART_MODE_STARTUP_ACTIVITY
    = new ExtensionPointName<>("com.intellij.requiredForSmartModeStartupActivity");

  public static final boolean ALWAYS_SMART = SystemProperties.getBooleanProperty("idea.no.dumb.mode", false);

  private static final Logger LOG = Logger.getInstance(DumbServiceImpl.class);
  private final AtomicReference<State> myState;
  private volatile Throwable myDumbStart;
  private final DumbModeListener myPublisher;
  private long myModificationCount;

  private final Project myProject;

  private final CancellableLaterEdtInvoker myCancellableLaterEdtInvoker;
  private final DumbServiceMergingTaskQueue myTaskQueue;
  private final DumbServiceGuiExecutor myGuiDumbTaskRunner;
  private final DumbServiceSyncTaskQueue mySyncDumbTaskRunner;
  private final DumbServiceAlternativeResolveTracker myAlternativeResolveTracker;

  //used from EDT
  private final DumbServiceBalloon myBalloon;

  private volatile @Nullable Thread myWaitIntolerantThread;

  // should only be accessed from EDT to avoid races between `queueTaskOnEDT` and `enterSmartModeIfDumb` (invoked from `afterLastTask`)
  private SubmissionReceipt myLatestReceipt;

  private class DumbTaskListener implements MergingQueueGuiExecutor.ExecutorStateListener {
    /*
     * beforeFirstTask and afterLastTask always follow one after another. Receiving several beforeFirstTask or afterLastTask in row is
     * always a failure of DumbServiceGuiTaskQueue.
     * return true to start queue processing, false otherwise
     */
    @Override
    public boolean beforeFirstTask() {
      // if a queue has already been emptied by modal dumb progress, DumbServiceGuiExecutor will not invoke processing on empty queue
      LOG.assertTrue(myState.get() == State.DUMB,
                     "State should be DUMB, but was " + myState.get());
      return true;
    }

    @Override
    public void afterLastTask(@Nullable SubmissionReceipt latestReceipt) {
      // If modality is null, then there is no dumb mode already (e.g. because the queue was processed under modal progress indicator)
      // There are no races: if DumbStartModality is about to change in myCancellableLaterEdtInvoker, we either observe:
      //   - if null modality about to change to non-null (happens only on EDT). This means that myLatestReceipt will also increase (on EDT)
      //   - if non-null modality about to change to null (happens only on EDT) then myState will also change to SMART (on EDT)
      // Either way, we don't need to execute the runnable. In the first case it will not be scheduled,
      // in the second case enterSmartModeIfDumb will do nothing.
      myCancellableLaterEdtInvoker.invokeLaterWithDumbStartModality(() -> {
        // Note that dumb service may already have been set to SMART state by completeJustSubmittedTasks.
        if (myLatestReceipt.equals(latestReceipt)) {
          enterSmartModeIfDumb();
        }
        // latestReceipt may be null if the queue is suspended or internal error has happened
        LOG.assertTrue(latestReceipt == null || !latestReceipt.isAfter(myLatestReceipt),
                       "latestReceipt=" + latestReceipt + " must not be newer than the latest known myLatestReceipt=" + myLatestReceipt);
      });
    }
  }

  public DumbServiceImpl(@NotNull Project project) {
    this(project, project.getMessageBus().syncPublisher(DUMB_MODE));
  }

  @NonInjectable
  @VisibleForTesting
  DumbServiceImpl(@NotNull Project project, @NotNull DumbModeListener publisher) {
    myProject = project;
    myCancellableLaterEdtInvoker = new CancellableLaterEdtInvoker(project);
    myTaskQueue = new DumbServiceMergingTaskQueue();
    myGuiDumbTaskRunner = new DumbServiceGuiExecutor(myProject, myTaskQueue, new DumbTaskListener());
    mySyncDumbTaskRunner = new DumbServiceSyncTaskQueue(myProject, myTaskQueue);

    myPublisher = publisher;

    if (Registry.is("scanning.should.pause.dumb.queue", false)) {
      myProject.getMessageBus().connect(this).subscribe(FilesScanningListener.TOPIC, new DumbServiceScanningListener(myProject));
    }
    if (Registry.is("vfs.refresh.should.pause.dumb.queue", true)) {
      new DumbServiceVfsBatchListener(myProject, myGuiDumbTaskRunner.getGuiSuspender());
    }
    myBalloon = new DumbServiceBalloon(project, this);
    myAlternativeResolveTracker = new DumbServiceAlternativeResolveTracker();
    // any project starts in dumb mode (except default project which is always smart)
    // we assume that queueStartupActivitiesRequiredForSmartMode will be invoked to advance DUMB > SMART
    myState = new AtomicReference<>(project.isDefault() ? State.SMART : State.DUMB);

    // the first dumb mode should end in non-modal context
    myCancellableLaterEdtInvoker.setDumbStartModality(ModalityState.NON_MODAL);
  }

  void queueStartupActivitiesRequiredForSmartMode() {
    queueTask(new InitialDumbTaskRequiredForSmartMode(getProject()));

    if (isSynchronousTaskExecution()) {
      // This is the same side effects as produced by enterSmartModeIfDumb (except updating icons). We apply them synchronously, because
      // invokeLaterWithDumbStartModality(this::enterSmartModeIfDumb) does not work well in synchronous environments (e.g. in unit tests):
      // code continues to execute without waiting for smart mode to start because of invoke*Later*. See, for example, DbSrcFileDialectTest
      myState.compareAndSet(State.DUMB, State.SMART);
      myCancellableLaterEdtInvoker.invokeLaterWithDumbStartModality(myPublisher::exitDumbMode);
    }
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static DumbServiceImpl getInstance(@NotNull Project project) {
    return (DumbServiceImpl)DumbService.getInstance(project);
  }

  @Override
  public void cancelTask(@NotNull DumbModeTask task) {
    if (ApplicationManager.getApplication().isInternal()) LOG.info("cancel " + task);
    myTaskQueue.cancelTask(task);
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    myBalloon.dispose();
    myCancellableLaterEdtInvoker.cancelAllPendingTasks();
    myTaskQueue.disposePendingTasks();
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean isAlternativeResolveEnabled() {
    return myAlternativeResolveTracker.isAlternativeResolveEnabled();
  }

  @Override
  public void suspendIndexingAndRun(@NotNull String activityName, @NotNull Runnable activity) {
    myGuiDumbTaskRunner.suspendAndRun(activityName, activity);
  }

  // non-public dangerous API. Use suspendIndexingAndRun instead
  MergingQueueGuiSuspender getGuiSuspender() {
    return myGuiDumbTaskRunner.getGuiSuspender();
  }

  @Override
  public void setAlternativeResolveEnabled(boolean enabled) {
    myAlternativeResolveTracker.setAlternativeResolveEnabled(enabled);
  }

  @Override
  public ModificationTracker getModificationTracker() {
    return this;
  }

  @Override
  public boolean isDumb() {
    if (ALWAYS_SMART) return false;
    if (!ApplicationManager.getApplication().isReadAccessAllowed() &&
        Registry.is("ide.check.is.dumb.contract")) {
      LOG.error("To avoid race conditions isDumb method should be used only under read action or in EDT thread.",
                           new IllegalStateException());
    }
    return myState.get() == State.DUMB;
  }

  @TestOnly
  public void setDumb(boolean dumb) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (dumb) {
      myState.set(State.DUMB);
      myPublisher.enteredDumbMode();
    }
    else {
      enterSmartModeIfDumb();
    }
  }

  @TestOnly
  public void runInDumbMode(@NotNull Runnable runnable) {
    setDumb(true);
    try {
      runnable.run();
    }
    finally {
      setDumb(false);
    }
  }

  @Override
  public void runWhenSmart(@Async.Schedule @NotNull Runnable runnable) {
    myProject.getService(SmartModeScheduler.class).runWhenSmart(runnable);
  }

  @Override
  public void unsafeRunWhenSmart(@NotNull @Async.Schedule Runnable runnable) {
    // we probably don't need unsafeRunWhenSmart anymore
    runWhenSmart(runnable);
  }

  @Override
  public void queueTask(@NotNull DumbModeTask task) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Scheduling task " + task);
    }
    if (myProject.isDefault()) {
      LOG.error("No indexing tasks should be created for default project: " + task);
    }

    if (isSynchronousTaskExecution()) {
      mySyncDumbTaskRunner.runTaskSynchronously(task);
      return;
    }

    Throwable trace = new Throwable();
    ModalityState modality = ModalityState.defaultModalityState();
    // we need EDT because enterDumbMode runs write action to make sure that we do not enter dumb mode during read actions
    Runnable runnable = () -> queueTaskOnEdt(task, modality, trace);
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run(); // will log errors if not already in a write-safe context
    }
    else {
      myCancellableLaterEdtInvoker.invokeLaterWithDefaultModality(runnable, () -> Disposer.dispose(task));
    }
  }

  private void queueTaskOnEdt(@NotNull DumbModeTask task, @NotNull ModalityState modality, @NotNull Throwable trace) {
    myLatestReceipt = myTaskQueue.addTask(task);
    enterDumbModeIfSmart(modality, trace);

    // we want to invoke LATER. I.e. right now one can invoke completeJustSubmittedTasks and
    // drain the queue synchronously under modal progress
    myCancellableLaterEdtInvoker.invokeLaterWithDumbStartModality(() -> {
      try {
        myGuiDumbTaskRunner.startBackgroundProcess();
      }
      catch (Throwable t) {
        // There are no evidences that returning to smart mode is a good strategy. Let it be like this until the opposite is needed.
        LOG.error("Failed to start background queue processing. Return to smart mode even though some tasks are still in the queue", t);
        enterSmartModeIfDumb();
      }
    });
  }

  private void enterDumbModeIfSmart(@NotNull ModalityState modality, @NotNull Throwable trace) {
    boolean entered = WriteAction.compute(() -> {
      if (!myState.compareAndSet(State.SMART, State.DUMB)) {
        return false;
      }
      myDumbStart = trace;
      myCancellableLaterEdtInvoker.setDumbStartModality(modality);
      myModificationCount++;
      return !myProject.isDisposed();
    });

    if (entered) {
      if (ApplicationManager.getApplication().isInternal()) LOG.info("entered dumb mode");
      runCatching(myPublisher::enteredDumbMode);
    }
  }

  private void enterSmartModeIfDumb() {
    boolean entered = WriteAction.compute(() -> {
      if (!myState.compareAndSet(State.DUMB, State.SMART)) {
        return false;
      }

      myDumbStart = null;
      myModificationCount++;
      myCancellableLaterEdtInvoker.setDumbStartModality(null);
      return !myProject.isDisposed();
    });

    if (entered) {
      if (ApplicationManager.getApplication().isInternal()) LOG.info("entered smart mode");
      runCatching(myPublisher::exitDumbMode);
    }
  }

  private static void runCatching(@NotNull Runnable runnable) {
    try {
      runnable.run();
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public void showDumbModeNotification(@NotNull @PopupContent String message) {
    UIUtil.invokeLaterIfNeeded(() -> {
      IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(myProject);
      if (ideFrame != null) {
        StatusBarEx statusBar = (StatusBarEx)ideFrame.getStatusBar();
        if (statusBar != null) {
          statusBar.notifyProgressByBalloon(MessageType.WARNING, message);
        }
      }
    });
  }

  @Override
  public void showDumbModeActionBalloon(@NotNull @PopupContent String balloonText,
                                        @NotNull Runnable runWhenSmartAndBalloonStillShowing) {
    myBalloon.showDumbModeActionBalloon(balloonText, runWhenSmartAndBalloonStillShowing);
  }

  @Override
  public void cancelAllTasksAndWait() {
    Application application = ApplicationManager.getApplication();
    if (!application.isWriteIntentLockAcquired() || application.isWriteAccessAllowed()) {
      throw new AssertionError("Must be called on write thread without write action");
    }

    LOG.info("Purge dumb task queue");

    Thread currentThread = Thread.currentThread();
    String initialThreadName = currentThread.getName();
    ConcurrencyUtil.runUnderThreadName(initialThreadName + " [DumbService.cancelAllTasksAndWait(state = " + myState.get() + ")]", () -> {
      // isRunning will be false eventually, because we are on EDT, and no new task can be queued outside the EDT
      // (we only wait for currently running task to terminate).
      myGuiDumbTaskRunner.cancelAllTasks();
      while (myGuiDumbTaskRunner.isRunning().getValue() && !myProject.isDisposed()) {
        PingProgress.interactWithEdtProgress();
        LockSupport.parkNanos(50_000_000);
      }

      // Invoked after myGuiDumbTaskRunner has stopped to make sure that all the tasks submitted from the executor callbacks are canceled
      // This also cancels all the tasks that are waiting for the EDT to queue new dumb tasks
      myCancellableLaterEdtInvoker.cancelAllPendingTasks();
    });
  }

  @Override
  public void waitForSmartMode() {
    waitForSmartMode(Long.MAX_VALUE);
  }

  public boolean waitForSmartMode(long milliseconds) {
    if (ALWAYS_SMART) return true;
    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed()) {
      throw new AssertionError("Don't invoke waitForSmartMode from inside read action in dumb mode");
    }
    if (myWaitIntolerantThread == Thread.currentThread()) {
      throw new AssertionError("Don't invoke waitForSmartMode from a background startup activity");
    }
    CountDownLatch switched = new CountDownLatch(1);
    SmartModeScheduler smartModeScheduler = myProject.getService(SmartModeScheduler.class);
    smartModeScheduler.runWhenSmart(switched::countDown);

    // we check getCurrentMode here because of tests which may hang because runWhenSmart needs EDT for scheduling
    long startTime = System.currentTimeMillis();
    while (!myProject.isDisposed() && smartModeScheduler.getCurrentMode() != 0) {
      // it is fine to unblock the caller when myProject.isDisposed, even if didn't reach smart mode: we are on background thread
      // without read action. Dumb mode may start immediately after the caller is unblocked, so caller is prepared for this situation.

      try {
        if (switched.await(50, TimeUnit.MILLISECONDS)) break;
      }
      catch (InterruptedException ignored) { }
      ProgressManager.checkCanceled();
      if (startTime + milliseconds < System.currentTimeMillis()) return false;
    }
    return true;
  }

  @Override
  public JComponent wrapGently(@NotNull JComponent dumbUnawareContent, @NotNull Disposable parentDisposable) {
    final DumbUnawareHider wrapper = new DumbUnawareHider(dumbUnawareContent);
    wrapper.setContentVisible(!isDumb());
    getProject().getMessageBus().connect(parentDisposable).subscribe(DUMB_MODE, new DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        wrapper.setContentVisible(false);
      }

      @Override
      public void exitDumbMode() {
        wrapper.setContentVisible(true);
      }
    });

    return wrapper;
  }

  @Override
  public JComponent wrapWithSpoiler(@NotNull JComponent dumbAwareContent, @NotNull Runnable updateRunnable, @NotNull Disposable parentDisposable) {
    //TODO replace with a proper mockup implementation
    DeprecationStripePanel stripePanel = new DeprecationStripePanel(IdeBundle.message("dumb.mode.results.might.be.incomplete"), AllIcons.General.Warning)
      .withAlternativeAction(IdeBundle.message("dumb.mode.spoiler.wrapper.reload.text"), new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          updateRunnable.run();
        }
      });
    stripePanel.setVisible(isDumb());
    getProject().getMessageBus().connect(parentDisposable).subscribe(DUMB_MODE, new DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        stripePanel.setVisible(true);
        updateRunnable.run();
      }

      @Override
      public void exitDumbMode() {
        stripePanel.setVisible(false);
        updateRunnable.run();
      }
    });
    return stripePanel.wrap(dumbAwareContent);
  }

  @Override
  public void smartInvokeLater(@NotNull Runnable runnable) {
    smartInvokeLater(runnable, ModalityState.defaultModalityState());
  }

  @Override
  public void smartInvokeLater(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (isDumb()) {
        runWhenSmart(() -> smartInvokeLater(runnable, modalityState));
      }
      else {
        runnable.run();
      }
    }, modalityState, myProject.getDisposed());
  }

  @Override
  public void completeJustSubmittedTasks() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    LOG.assertTrue(myProject.isInitialized(), "Project should have been initialized");

    while (myState.get() == State.DUMB) {
      boolean queueProcessedUnderModalProgress = processQueueUnderModalProgress();
      if (!queueProcessedUnderModalProgress) {
        // processQueueUnderModalProgress did nothing (i.e. processing is being done under non-modal indicator)
        break;
      }
    }

    if (myState.get() == State.SMART) { // we can reach this statement in dumb mode if queue is processed in background
      // DumbServiceSyncTaskQueue does not respect threading policies: it can add tasks outside of EDT
      // and process them without switching to dumb mode. This behavior has to be fixed, but for now just ignore
      // it, because it has been working like this for years already.
      // Reproducing in test: com.jetbrains.cidr.lang.refactoring.OCRenameMoveFileTest
      LOG.assertTrue(isSynchronousTaskExecution() || myTaskQueue.isEmpty(), "Task queue is not empty. Current state is " + myState.get());
    }
  }

  private boolean processQueueUnderModalProgress() {
    NoAccessDuringPsiEvents.checkCallContext("modal indexing");

    return myGuiDumbTaskRunner.tryStartProcessInThisThread(processTask -> {
      try {
        ((ApplicationImpl)ApplicationManager.getApplication()).executeSuspendingWriteAction(myProject, IndexingBundle.message("progress.indexing.title"), () -> {
          try(processTask) {
            processTask.run(ProgressManager.getInstance().getProgressIndicator());
          }
        });
      }
      finally {
        if (myTaskQueue.isEmpty()) {
          enterSmartModeIfDumb();
        }
      }
    });
  }

  @Override
  public AccessToken runWithWaitForSmartModeDisabled() {
    myWaitIntolerantThread = Thread.currentThread();
    return new AccessToken() {
      @Override
      public void finish() {
        myWaitIntolerantThread = null;
      }
    };
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  public @Nullable Throwable getDumbModeStartTrace() {
    return myDumbStart;
  }

  private enum State {SMART, DUMB}

  public static boolean isSynchronousTaskExecution() {
    Application application = ApplicationManager.getApplication();
    return (application.isUnitTestMode() || application.isHeadlessEnvironment()) && !Boolean.parseBoolean(System.getProperty("idea.force.dumb.queue.tasks", "false"));
  }
}
