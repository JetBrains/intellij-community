// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeWithMe.ClientId;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.internal.statistic.IdeActivity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.RelayUiToDelegateIndicator;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.util.containers.Queue;
import com.intellij.util.exception.FrequentErrorLogger;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.ui.DeprecationStripePanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class DumbServiceImpl extends DumbService implements Disposable, ModificationTracker, DumbServiceBalloon.Service {
  private static final ExtensionPointName<StartupActivity.RequiredForSmartMode> REQUIRED_FOR_SMART_MODE_STARTUP_ACTIVITY
    = new ExtensionPointName<>("com.intellij.requiredForSmartModeStartupActivity");

  private static final Logger LOG = Logger.getInstance(DumbServiceImpl.class);
  private static final FrequentErrorLogger ourErrorLogger = FrequentErrorLogger.newInstance(LOG);
  private final AtomicReference<State> myState;
  private volatile Throwable myDumbEnterTrace;
  private volatile Throwable myDumbStart;
  private final DumbModeListener myPublisher;
  private long myModificationCount;


  private final Queue<Runnable> myRunWhenSmartQueue = new Queue<>(5);
  private final Project myProject;

  private final TrackedEdtActivityService myTrackedEdtActivityService;
  private final DumbServiceMergingTaskQueue myTaskQueue;
  private final DumbServiceGuiTaskQueue myGuiDumbTaskRunner;
  private final DumbServiceSyncTaskQueue mySyncDumbTaskRunner;
  private final DumbServiceHeavyActivities myHeavyActivities;
  private final DumbServiceAlternativeResolveTracker myAlternativeResolveTracker;

  //used from EDT
  private final DumbServiceBalloon myBalloon;

  public DumbServiceImpl(Project project) {
    myProject = project;
    myTrackedEdtActivityService = new TrackedEdtActivityService(project);
    myTaskQueue = new DumbServiceMergingTaskQueue();
    myGuiDumbTaskRunner = new DumbServiceGuiTaskQueue(myProject, myTaskQueue);
    mySyncDumbTaskRunner = new DumbServiceSyncTaskQueue(myTaskQueue);

    myPublisher = project.getMessageBus().syncPublisher(DUMB_MODE);

    myHeavyActivities = new DumbServiceHeavyActivities();
    new DumbServiceVfsBatchListener(myProject, myHeavyActivities);
    myBalloon = new DumbServiceBalloon(project, this);
    myAlternativeResolveTracker = new DumbServiceAlternativeResolveTracker();
    myState = new AtomicReference<>(project.isDefault() ? State.SMART : State.WAITING_PROJECT_SMART_MODE_STARTUP_TASKS);
  }

  void queueStartupActivitiesRequiredForSmartMode() {
    LOG.assertTrue(myState.compareAndSet(State.WAITING_PROJECT_SMART_MODE_STARTUP_TASKS,
                                         State.RUNNING_PROJECT_SMART_MODE_STARTUP_TASKS),
                   "actual state: " + myState.get() + ", project " + getProject());

    List<StartupActivity.RequiredForSmartMode> activities = REQUIRED_FOR_SMART_MODE_STARTUP_ACTIVITY.getExtensionList();
    if (activities.isEmpty()) {
      myState.set(State.SMART);
    }
    else {
      for (StartupActivity.RequiredForSmartMode activity : activities) {
        activity.runActivity(getProject());
      }

      if (isSynchronousTaskExecution()) {
        myState.set(State.SMART);
      }
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
    ApplicationManager.getApplication().assertIsWriteThread();
    myBalloon.dispose();

    synchronized (myRunWhenSmartQueue) {
      myRunWhenSmartQueue.clear();
    }
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
    myHeavyActivities.suspendIndexingAndRun(activityName, activity);
  }

  @Override
  public boolean isSuspendedDumbMode() {
    boolean suspended = myHeavyActivities.isSuspended();
    return isDumb() && suspended;
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
    if (!ApplicationManager.getApplication().isReadAccessAllowed() &&
        Registry.is("ide.check.is.dumb.contract")) {
      ourErrorLogger.error("To avoid race conditions isDumb method should be used only under read action or in EDT thread.",
                           new IllegalStateException());
    }
    return myState.get() != State.SMART;
  }

  @TestOnly
  public void setDumb(boolean dumb) {
    if (dumb) {
      myState.set(State.RUNNING_DUMB_TASKS);
      myPublisher.enteredDumbMode();
    }
    else {
      myState.set(State.WAITING_FOR_FINISH);
      updateFinished();
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
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> unsafeRunWhenSmart(runnable));
  }

  @Override
  public void unsafeRunWhenSmart(@NotNull @Async.Schedule Runnable runnable) {
    synchronized (myRunWhenSmartQueue) {
      if (isDumb()) {
        myRunWhenSmartQueue.addLast(ClientId.decorateRunnable(runnable));
        return;
      }
    }

    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(() -> unsafeRunWhenSmart(runnable), ModalityState.NON_MODAL, myProject.getDisposed());
    }
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
    }
    else {
      queueAsynchronousTask(task);
    }
  }

  @VisibleForTesting
  private void queueAsynchronousTask(@NotNull DumbModeTask task) {
    Throwable trace = new Throwable(); // please report exceptions here to peter
    ModalityState modality = ModalityState.defaultModalityState();
    Runnable runnable = () -> queueTaskOnEdt(task, modality, trace);
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run(); // will log errors if not already in a write-safe context
    }
    else {
      TransactionGuard.submitTransaction(myProject, runnable);
    }
  }

  private void queueTaskOnEdt(@NotNull DumbModeTask task, @NotNull ModalityState modality, @NotNull Throwable trace) {
    myTaskQueue.addTask(task);
    State state = myState.get();
    if (state == State.WAITING_PROJECT_SMART_MODE_STARTUP_TASKS) {
      return;
    }

    if (state == State.SMART ||
        state == State.WAITING_FOR_FINISH ||
        state == State.RUNNING_PROJECT_SMART_MODE_STARTUP_TASKS) {
      enterDumbMode(modality, trace);
      myTrackedEdtActivityService.invokeLaterIfProjectNotDisposed(this::startBackgroundProcess);
    }
  }

  private void enterDumbMode(@NotNull ModalityState modality, @NotNull Throwable trace) {
    boolean wasSmart = !isDumb();
    WriteAction.run(() -> {
      synchronized (myRunWhenSmartQueue) {
        myState.set(State.SCHEDULED_TASKS);
      }
      myDumbStart = trace;
      myDumbEnterTrace = new Throwable();
      myTrackedEdtActivityService.setDumbStartModality(modality);
      myModificationCount++;
    });
    if (wasSmart) {
      try {
        myPublisher.enteredDumbMode();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  private void queueUpdateFinished() {
    if (myState.compareAndSet(State.RUNNING_DUMB_TASKS, State.WAITING_FOR_FINISH)) {
      // There is no task to suspend with the current suspender. If the execution reverts to the dumb mode, a new suspender will be
      // created.
      // The current suspender, however, might have already got suspended between the point of the last check cancelled call and
      // this point. If it has happened it will be cleaned up when the suspender is closed on the background process thread.
      myHeavyActivities.resetCurrentSuspender();
      myTrackedEdtActivityService.invokeLaterAfterProjectInitialized(this::updateFinished);
    }
  }

  private boolean switchToSmartMode() {
    synchronized (myRunWhenSmartQueue) {
      if (!myState.compareAndSet(State.WAITING_FOR_FINISH, State.SMART)) {
        return false;
      }
    }

    StartUpMeasurer.compareAndSetCurrentState(LoadingState.PROJECT_OPENED, LoadingState.INDEXING_FINISHED);

    myDumbEnterTrace = null;
    myDumbStart = null;
    myModificationCount++;
    return !myProject.isDisposed();
  }

  private void updateFinished() {
    if (!WriteAction.compute(this::switchToSmartMode)) return;

    if (ApplicationManager.getApplication().isInternal()) LOG.info("updateFinished");

    try {
      myPublisher.exitDumbMode();
      FileEditorManagerEx.getInstanceEx(myProject).refreshIcons();
    }
    finally {
      // It may happen that one of the pending runWhenSmart actions triggers new dumb mode;
      // in this case we should quit processing pending actions and postpone them until the newly started dumb mode finishes.
      while (!isDumb()) {
        final Runnable runnable;
        synchronized (myRunWhenSmartQueue) {
          if (myRunWhenSmartQueue.isEmpty()) {
            break;
          }
          runnable = myRunWhenSmartQueue.pullFirst();
        }
        doRun(runnable);
      }
    }
  }

  // Extracted to have a capture point
  private static void doRun(@Async.Execute Runnable runnable) {
    try {
      runnable.run();
    }
    catch (ProcessCanceledException e) {
      LOG.error("Task canceled: " + runnable, new Attachment("pce", e));
    }
    catch (Throwable e) {
      LOG.error("Error executing task " + runnable, e);
    }
  }

  @Override
  public void showDumbModeNotification(@NotNull @PopupContent String message) {
    UIUtil.invokeLaterIfNeeded(() -> {
      final IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(myProject);
      if (ideFrame != null) {
        ((StatusBarEx)ideFrame.getStatusBar()).notifyProgressByBalloon(MessageType.WARNING, message);
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
    if (!application.isWriteThread() || application.isWriteAccessAllowed()) {
      throw new AssertionError("Must be called on write thread without write action");
    }

    while (!(myState.get() == State.SMART ||
             myState.get() == State.WAITING_PROJECT_SMART_MODE_STARTUP_TASKS)
           && !myProject.isDisposed()) {
      LockSupport.parkNanos(50_000_000);
      // polls next dumb mode task
      myTrackedEdtActivityService.executeAllQueuedActivities();
      // cancels all scheduled and running tasks
      myTaskQueue.cancelAllTasks();
      myHeavyActivities.resumeProgressIfPossible();
    }
  }

  @Override
  public void waitForSmartMode() {
    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed() || application.isDispatchThread()) {
      throw new AssertionError("Don't invoke waitForSmartMode from inside read action in dumb mode");
    }

    while (myState.get() != State.SMART && !myProject.isDisposed()) {
      LockSupport.parkNanos(50_000_000);
      ProgressManager.checkCanceled();
    }
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
    DeprecationStripePanel stripePanel = new DeprecationStripePanel(IdeBundle.message("dumb.mode.spoiler.wrapper.text"), AllIcons.General.Warning)
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
  public void smartInvokeLater(final @NotNull Runnable runnable) {
    smartInvokeLater(runnable, ModalityState.defaultModalityState());
  }

  @Override
  public void smartInvokeLater(final @NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (isDumb()) {
        runWhenSmart(() -> smartInvokeLater(runnable, modalityState));
      } else {
        runnable.run();
      }
    }, modalityState, myProject.getDisposed());
  }

  @Override
  public void completeJustSubmittedTasks() {
    ApplicationManager.getApplication().assertIsWriteThread();
    assert myProject.isInitialized();
    if (myState.get() != State.SCHEDULED_TASKS) {
      return;
    }
    while (isDumb()) {
      assertState(State.SCHEDULED_TASKS);
      showModalProgress();
    }
  }

  private void showModalProgress() {
    NoAccessDuringPsiEvents.checkCallContext("modal indexing");
    try {
      ((ApplicationImpl)ApplicationManager.getApplication()).executeSuspendingWriteAction(myProject, IndexingBundle.message("progress.indexing"), () -> {
        assertState(State.SCHEDULED_TASKS);
        runBackgroundProcess(ProgressManager.getInstance().getProgressIndicator());
        assertState(State.SMART, State.WAITING_FOR_FINISH);
      });
      assertState(State.SMART, State.WAITING_FOR_FINISH);
    }
    finally {
      if (myState.get() != State.SMART) {
        assertState(State.WAITING_FOR_FINISH);
        updateFinished();
        assertState(State.SMART, State.SCHEDULED_TASKS);
      }
    }
  }

  private void assertState(State... expected) {
    State state = myState.get();
    List<State> expectedList = Arrays.asList(expected);
    if (!expectedList.contains(state)) {
      List<Attachment> attachments = new ArrayList<>();
      if (myDumbEnterTrace != null) {
        attachments.add(new Attachment("indexingStart", myDumbEnterTrace));
      }
      attachments.add(new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString()));
      throw new RuntimeExceptionWithAttachments("Internal error, please include thread dump attachment. " +
                                                "Expected " + expectedList + ", but was " + state.toString(),
                                                attachments.toArray(Attachment.EMPTY_ARRAY));
    }
  }

  private void startBackgroundProcess() {
    try {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, IndexingBundle.message("progress.indexing"), false) {
        @Override
        public void run(final @NotNull ProgressIndicator visibleIndicator) {
          runBackgroundProcess(visibleIndicator);
        }
      });
    }
    catch (Throwable e) {
      queueUpdateFinished();
      LOG.error("Failed to start background index update task", e);
    }
  }

  private void runBackgroundProcess(final @NotNull ProgressIndicator visibleIndicator) {
    ((ProgressManagerImpl)ProgressManager.getInstance()).markProgressSafe((UserDataHolder)visibleIndicator);

    if (!myState.compareAndSet(State.SCHEDULED_TASKS, State.RUNNING_DUMB_TASKS)) return;

    // Only one thread can execute this method at the same time at this point.

    try (ProgressSuspender suspender = ProgressSuspender.markSuspendable(visibleIndicator, "Indexing paused")) {
      myHeavyActivities.setCurrentSuspenderAndSuspendIfRequested(suspender);

      IdeActivity activity = IdeActivity.started(myProject, "indexing");
      final ShutDownTracker shutdownTracker = ShutDownTracker.getInstance();
      final Thread self = Thread.currentThread();
      try {
        shutdownTracker.registerStopperThread(self);

        DumbServiceAppIconProgress.registerForProgress(myProject, (ProgressIndicatorEx)visibleIndicator);
        ProgressIndicatorEx relayToVisibleIndicator = new RelayUiToDelegateIndicator(visibleIndicator);

        myGuiDumbTaskRunner.processTasksWithProgress(activity, taskIndicator -> {
          suspender.attachToProgress(taskIndicator);
          taskIndicator.addStateDelegate(relayToVisibleIndicator);
        },
        taskIndicator -> {
          ((AbstractProgressIndicatorExBase)taskIndicator).removeStateDelegate(relayToVisibleIndicator);
        }
        );
      }
      catch (Throwable unexpected) {
        LOG.error(unexpected);
      }
      finally {
        shutdownTracker.unregisterStopperThread(self);
        // myCurrentSuspender should already be null at this point unless we got here by exception. In any case, the suspender might have
        // got suspended after the the last dumb task finished (or even after the last check cancelled call). This case is handled by
        // the ProgressSuspender close() method called at the exit of this try-with-resources block which removes the hook if it has been
        // previously installed.
        myHeavyActivities.resetCurrentSuspender();

        //this used to be called in EDT from getNextTask(), but moved it here to simplify
        queueUpdateFinished();

        activity.finished();
      }
    }
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  public @Nullable Throwable getDumbModeStartTrace() {
    return myDumbStart;
  }

  private enum State {
    /** Non-dumb mode. For all other states, {@link #isDumb()} returns true. */
    SMART,

    /**
     * A state between entering dumb mode ({@link #queueTaskOnEdt}) and actually starting the background progress later ({@link #runBackgroundProcess}).
     * In this state, it's possible to call {@link #completeJustSubmittedTasks()} and perform all submitted the tasks modality.
     * This state can happen after {@link #SMART} or {@link #WAITING_FOR_FINISH}. Followed by {@link #RUNNING_DUMB_TASKS}.
     */
    SCHEDULED_TASKS,

    /**
     * Indicates that a background thread is currently executing dumb tasks.
     */
    RUNNING_DUMB_TASKS,

    /**
     * Set after background execution ({@link #RUNNING_DUMB_TASKS}) finishes, until the dumb mode can be exited
     * (in a write-safe context on EDT when project is initialized). If new tasks are queued at this state, it's switched to {@link #SCHEDULED_TASKS}.
     */
    WAITING_FOR_FINISH,

    /**
     * Indicates that project has been just loaded and
     * {@link StartupActivity.RequiredForSmartMode}-s were not submitted to execution to ensure project smart mode.
     */
    WAITING_PROJECT_SMART_MODE_STARTUP_TASKS,

    /**
     * Indicates that project has been loaded and {@link StartupActivity.RequiredForSmartMode}-s were added to task queue.
     */
    RUNNING_PROJECT_SMART_MODE_STARTUP_TASKS
  }

  private static boolean isSynchronousTaskExecution() {
    Application application = ApplicationManager.getApplication();
    return (application.isUnitTestMode() || application.isHeadlessEnvironment()) && !Boolean.parseBoolean(System.getProperty("idea.force.dumb.queue.tasks", "false"));
  }
}
