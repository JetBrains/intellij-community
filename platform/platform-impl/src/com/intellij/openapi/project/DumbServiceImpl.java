// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeWithMe.ClientId;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.internal.statistic.IdeActivity;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.UIEventId;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.AppIcon;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Queue;
import com.intellij.util.exception.FrequentErrorLogger;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.DeprecationStripePanel;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class DumbServiceImpl extends DumbService implements Disposable, ModificationTracker {
  private static final Logger LOG = Logger.getInstance(DumbServiceImpl.class);
  private static final FrequentErrorLogger ourErrorLogger = FrequentErrorLogger.newInstance(LOG);
  private static final @NotNull JBInsets DUMB_BALLOON_INSETS = JBInsets.create(5, 8);
  private final AtomicReference<State> myState;
  private volatile Throwable myDumbEnterTrace;
  private volatile Throwable myDumbStart;
  private volatile ModalityState myDumbStartModality;
  private final DumbModeListener myPublisher;
  private long myModificationCount;
  private final Set<Object> myQueuedEquivalences = new HashSet<>();
  private final Queue<DumbModeTask> myUpdatesQueue = new Queue<>(5);

  /**
   * Per-task progress indicators. Modified from EDT only.
   * The task is removed from this map after it's finished or when the project is disposed.
   */
  private final Map<DumbModeTask, ProgressIndicatorEx> myProgresses = new ConcurrentHashMap<>();
  private Balloon myBalloon;//used from EDT only

  private final Queue<Runnable> myRunWhenSmartQueue = new Queue<>(5);
  private final Project myProject;
  private final ThreadLocal<Integer> myAlternativeResolution = new ThreadLocal<>();
  private volatile ProgressSuspender myCurrentSuspender;
  private final List<String> myRequestedSuspensions = ContainerUtil.createEmptyCOWList();
   private final BlockingQueue<TrackedEdtActivity> myTrackedEdtActivities = new LinkedBlockingQueue<>();

  public DumbServiceImpl(Project project) {
    myProject = project;
    myPublisher = project.getMessageBus().syncPublisher(DUMB_MODE);

    ApplicationManager.getApplication().getMessageBus().connect(project)
      .subscribe(BatchFileChangeListener.TOPIC, new BatchFileChangeListener() {
        // synchronized, can be accessed from different threads
        @SuppressWarnings("UnnecessaryFullyQualifiedName")
        final java.util.Stack<AccessToken> stack = new Stack<>();

        @Override
        public void batchChangeStarted(@NotNull Project project, @Nullable String activityName) {
          if (project == myProject) {
            stack.push(heavyActivityStarted(activityName != null ? UIUtil.removeMnemonic(activityName) : "file system changes"));
          }
        }

        @Override
        public void batchChangeCompleted(@NotNull Project project) {
          if (project != myProject) return;

          Stack<AccessToken> tokens = stack;
          if (!tokens.isEmpty()) { // just in case
            tokens.pop().finish();
          }
        }
      });
    myState = new AtomicReference<>(project.isDefault() ? State.SMART : State.WAITING_PROJECT_SMART_MODE_STARTUP_TASKS);
  }

  void queueStartupActivitiesRequiredForSmartMode() {
    LOG.assertTrue(myState.get() == State.WAITING_PROJECT_SMART_MODE_STARTUP_TASKS, "actual state: " + myState.get() + ", project " + getProject());

    List<StartupActivity.RequiredForSmartMode> activities = StartupActivity
      .REQUIRED_FOR_SMART_MODE_STARTUP_ACTIVITY
      .getExtensionList();

    if (activities.isEmpty()) {
      myState.set(State.SMART);
    } else {
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
    ProgressIndicatorEx indicator = myProgresses.get(task);
    if (indicator != null) {
      indicator.cancel();
    }
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertIsWriteThread();
    if (myBalloon != null) {
      Disposer.dispose(myBalloon);
    }
    myUpdatesQueue.clear();
    myQueuedEquivalences.clear();
    synchronized (myRunWhenSmartQueue) {
      myRunWhenSmartQueue.clear();
    }
    for (DumbModeTask task : new ArrayList<>(myProgresses.keySet())) {
      cancelTask(task);
      Disposer.dispose(task);
    }
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public boolean isAlternativeResolveEnabled() {
    return myAlternativeResolution.get() != null;
  }

  @Override
  public void suspendIndexingAndRun(@NotNull String activityName, @NotNull Runnable activity) {
    try (AccessToken ignore = heavyActivityStarted(activityName)) {
      activity.run();
    }
  }

  @Override
  public boolean isSuspendedDumbMode() {
    ProgressSuspender suspender = myCurrentSuspender;
    return isDumb() && suspender != null && suspender.isSuspended();
  }

  private @NotNull AccessToken heavyActivityStarted(@NotNull String activityName) {
    String reason = "Indexing paused due to " + activityName;
    synchronized (myRequestedSuspensions) {
      myRequestedSuspensions.add(reason);
    }

    suspendCurrentTask(reason);
    return new AccessToken() {
      @Override
      public void finish() {
        synchronized (myRequestedSuspensions) {
          myRequestedSuspensions.remove(reason);
        }
        resumeAutoSuspendedTask(reason);
      }
    };
  }

  private void suspendCurrentTask(String reason) {
    ProgressSuspender currentSuspender = myCurrentSuspender;
    if (currentSuspender != null && !currentSuspender.isSuspended()) {
      currentSuspender.suspendProcess(reason);
    }
  }

  private void resumeAutoSuspendedTask(@NotNull String reason) {
    ProgressSuspender currentSuspender = myCurrentSuspender;
    if (currentSuspender != null && currentSuspender.isSuspended() && reason.equals(currentSuspender.getSuspendedText())) {
      currentSuspender.resumeProcess();
    }
  }

  private void suspendIfRequested(ProgressSuspender suspender) {
    synchronized (myRequestedSuspensions) {
      String suspendedReason = ContainerUtil.getLastItem(myRequestedSuspensions);
      if (suspendedReason != null) {
        suspender.suspendProcess(suspendedReason);
      }
    }
  }

  @Override
  public void setAlternativeResolveEnabled(boolean enabled) {
    Integer oldValue = myAlternativeResolution.get();
    int newValue = (oldValue == null ? 0 : oldValue) + (enabled ? 1 : -1);
    assert newValue >= 0 : "Non-paired alternative resolution mode";
    myAlternativeResolution.set(newValue == 0 ? null : newValue);
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

    runnable.run();
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
      runTaskSynchronously(task);
    }
    else {
      queueAsynchronousTask(task);
    }
  }

  private static void runTaskSynchronously(@NotNull DumbModeTask task) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null) {
      indicator = new EmptyProgressIndicator();
    }

    indicator.pushState();
    ((CoreProgressManager)ProgressManager.getInstance()).suppressPrioritizing();
    try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing task", HeavyProcessLatch.Type.Indexing)) {
      task.performInDumbMode(indicator);
    }
    finally {
      ((CoreProgressManager)ProgressManager.getInstance()).restorePrioritizing();
      indicator.popState();
      Disposer.dispose(task);
    }
  }

  @VisibleForTesting
  void queueAsynchronousTask(@NotNull DumbModeTask task) {
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
    if (!addTaskToQueue(task)) return;

    State state = myState.get();
    if (state == State.SMART ||
        state == State.WAITING_FOR_FINISH ||
        state == State.WAITING_PROJECT_SMART_MODE_STARTUP_TASKS) {
      enterDumbMode(modality, trace);
      new TrackedEdtActivity(this::startBackgroundProcess).invokeLaterIfProjectNotDisposed();
    }
  }

  private boolean addTaskToQueue(@NotNull DumbModeTask task) {
    if (!myQueuedEquivalences.add(task.getEquivalenceObject())) {
      Disposer.dispose(task);
      return false;
    }

    myProgresses.put(task, new ProgressIndicatorBase());
    Disposer.register(task, () -> {
      ApplicationManager.getApplication().assertIsWriteThread();
      myProgresses.remove(task);
    });
    myUpdatesQueue.addLast(task);
    return true;
  }

  private void enterDumbMode(@NotNull ModalityState modality, @NotNull Throwable trace) {
    boolean wasSmart = !isDumb();
    WriteAction.run(() -> {
      synchronized (myRunWhenSmartQueue) {
        myState.set(State.SCHEDULED_TASKS);
      }
      myDumbStart = trace;
      myDumbEnterTrace = new Throwable();
      myDumbStartModality = modality;
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
      myCurrentSuspender = null;
      new TrackedEdtActivity(this::updateFinished).invokeLaterAfterProjectInitialized();
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
    if (LightEdit.owns(myProject)) return;
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!isDumb()) {
      UIEventLogger.logUIEvent(UIEventId.DumbModeBalloonWasNotNeeded, new FeatureUsageData().addProject(myProject));
      runWhenSmartAndBalloonStillShowing.run();
      return;
    }
    if (myBalloon != null) {
      //here should be an assertion that it does not happen, but now we have two dispatches of one InputEvent, see IDEA-227444
      return;
    }
    tryShowBalloonTillSmartMode(balloonText, runWhenSmartAndBalloonStillShowing);
  }

  private void tryShowBalloonTillSmartMode(@NotNull @PopupContent String balloonText,
                                           @NotNull Runnable runWhenSmartAndBalloonNotHidden) {
    LOG.assertTrue(myBalloon == null);
    long startTimestamp = System.nanoTime();
    UIEventLogger.logUIEvent(UIEventId.DumbModeBalloonRequested, new FeatureUsageData().addProject(myProject));
    myBalloon = JBPopupFactory.getInstance().
            createHtmlTextBalloonBuilder(balloonText, AllIcons.General.BalloonWarning, UIUtil.getToolTipBackground(), null).
            setBorderColor(JBColor.border()).
            setBorderInsets(DUMB_BALLOON_INSETS).
            setShowCallout(false).
            createBalloon();
    myBalloon.setAnimationEnabled(false);
    myBalloon.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        if (myBalloon == null) {
          return;
        }
        FeatureUsageData data = new FeatureUsageData().addProject(myProject);
        UIEventLogger.logUIEvent(UIEventId.DumbModeBalloonCancelled, data);
        myBalloon = null;
      }
    });
    runWhenSmart(() -> {
      if (myBalloon == null) {
        return;
      }
      FeatureUsageData data = new FeatureUsageData().addProject(myProject).
        addData("duration_ms", TimeoutUtil.getDurationMillis(startTimestamp));
      UIEventLogger.logUIEvent(UIEventId.DumbModeBalloonProceededToActions, data);
      runWhenSmartAndBalloonNotHidden.run();
      Balloon balloon = myBalloon;
      myBalloon = null;
      balloon.hide();
    });
    DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(context -> {
      if (!isDumb()) {
        return;
      }
      if (myBalloon == null) {
        return;
      }
      UIEventLogger.logUIEvent(UIEventId.DumbModeBalloonShown, new FeatureUsageData().addProject(myProject));
      myBalloon.show(getDumbBalloonPopupPoint(myBalloon, context), Balloon.Position.above);
    });
  }

  private static @NotNull RelativePoint getDumbBalloonPopupPoint(@NotNull Balloon balloon, DataContext context) {
    RelativePoint relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(context);
    Dimension size = balloon.getPreferredSize();
    Point point = relativePoint.getPoint();
    point.translate(size.width / 2, 0);
    //here are included hardcoded insets, icon width and small hardcoded delta to show before guessBestPopupLocation point
    point.translate(-DUMB_BALLOON_INSETS.left - AllIcons.General.BalloonWarning.getIconWidth() - JBUIScale.scale(6), 0);
    return new RelativePoint(relativePoint.getComponent(), point);
  }

  @Override
  public void cancelAllTasksAndWait() {
    Application application = ApplicationManager.getApplication();
    if (!application.isWriteThread() || application.isWriteAccessAllowed()) {
      throw new AssertionError("Must be called on write thread without write action");
    }

    while (myState.get() != State.SMART && !myProject.isDisposed()) {
      LockSupport.parkNanos(50_000_000);
      // polls next dumb mode task
      while (!myTrackedEdtActivities.isEmpty()) {
        myTrackedEdtActivities.poll().run();
      }
      // cancels all scheduled and running tasks
      for (DumbModeTask task : myProgresses.keySet()) {
        cancelTask(task);
      }

      if (myCurrentSuspender != null && myCurrentSuspender.isSuspended()) {
        myCurrentSuspender.resumeProcess();
      }
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
    ((ProgressManagerImpl)ProgressManager.getInstance()).markProgressSafe((ProgressIndicatorBase)visibleIndicator);

    if (!myState.compareAndSet(State.SCHEDULED_TASKS, State.RUNNING_DUMB_TASKS)) return;

    // Only one thread can execute this method at the same time at this point.

    try (ProgressSuspender suspender = ProgressSuspender.markSuspendable(visibleIndicator, "Indexing paused")) {
      myCurrentSuspender = suspender;
      suspendIfRequested(suspender);

      IdeActivity activity = IdeActivity.started(myProject, "indexing");
      final ShutDownTracker shutdownTracker = ShutDownTracker.getInstance();
      final Thread self = Thread.currentThread();
      try {
        shutdownTracker.registerStopperThread(self);

        ((ProgressIndicatorEx)visibleIndicator).addStateDelegate(new AppIconProgress());

        DumbModeTask task = null;
        while (true) {
          Pair<DumbModeTask, ProgressIndicatorEx> pair = getNextTask(task);
          if (pair == null) break;

          task = pair.first;
          activity.stageStarted(task.getClass());
          ProgressIndicatorEx taskIndicator = pair.second;
          suspender.attachToProgress(taskIndicator);
          taskIndicator.addStateDelegate(new AbstractProgressIndicatorExBase() {
            @Override
            protected void delegateProgressChange(@NotNull IndicatorAction action) {
              super.delegateProgressChange(action);
              action.execute((ProgressIndicatorEx)visibleIndicator);
            }
          });
          try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing tasks", HeavyProcessLatch.Type.Indexing)) {
            runSingleTask(task, taskIndicator);
          }
        }
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
        myCurrentSuspender = null;
        activity.finished();
      }
    }
  }

  private static void runSingleTask(final DumbModeTask task, final ProgressIndicatorEx taskIndicator) {
    if (ApplicationManager.getApplication().isInternal()) LOG.info("Running dumb mode task: " + task);

    // nested runProcess is needed for taskIndicator to be honored in ProgressManager.checkCanceled calls deep inside tasks
    ProgressManager.getInstance().runProcess(() -> {
      try {
        taskIndicator.checkCanceled();
        taskIndicator.setIndeterminate(true);
        task.performInDumbMode(taskIndicator);
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (Throwable unexpected) {
        LOG.error(unexpected);
      }
    }, taskIndicator);
  }

  private @Nullable Pair<DumbModeTask, ProgressIndicatorEx> getNextTask(@Nullable DumbModeTask prevTask) {
    CompletableFuture<Pair<DumbModeTask, ProgressIndicatorEx>> result = new CompletableFuture<>();
    new TrackedEdtActivity(() -> {
      if (myProject.isDisposed()) {
        result.completeExceptionally(new ProcessCanceledException());
        return;
      }

      if (prevTask != null) {
        Disposer.dispose(prevTask);
      }

      result.complete(pollTaskQueue());
    }).invokeLater();
    return waitForFuture(result);
  }

  private @Nullable Pair<DumbModeTask, ProgressIndicatorEx> pollTaskQueue() {
    while (true) {
      if (myUpdatesQueue.isEmpty()) {
        queueUpdateFinished();
        return null;
      }

      DumbModeTask queuedTask = myUpdatesQueue.pullFirst();
      myQueuedEquivalences.remove(queuedTask.getEquivalenceObject());
      ProgressIndicatorEx indicator = myProgresses.get(queuedTask);
      if (indicator.isCanceled()) {
        Disposer.dispose(queuedTask);
        continue;
      }

      return Pair.create(queuedTask, indicator);
    }
  }

  private static @Nullable <T> T waitForFuture(Future<T> result) {
    try {
      return result.get();
    }
    catch (InterruptedException e) {
      return null;
    }
    catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (!(cause instanceof ProcessCanceledException)) {
        ExceptionUtil.rethrowAllAsUnchecked(cause);
      }
      return null;
    }
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  public @Nullable Throwable getDumbModeStartTrace() {
    return myDumbStart;
  }

  private class AppIconProgress extends ProgressIndicatorBase {
    private double lastFraction;

    @Override
    public void setFraction(final double fraction) {
      if (fraction - lastFraction < 0.01d) return;
      lastFraction = fraction;
      UIUtil.invokeLaterIfNeeded(
        () -> AppIcon.getInstance().setProgress(myProject, "indexUpdate", AppIconScheme.Progress.INDEXING, fraction, true));
    }

    @Override
    public void finish(@NotNull TaskInfo task) {
      if (lastFraction != 0) { // we should call setProgress at least once before
        UIUtil.invokeLaterIfNeeded(() -> {
          AppIcon appIcon = AppIcon.getInstance();
          if (appIcon.hideProgress(myProject, "indexUpdate")) {
            if (Registry.is("ide.appIcon.requestAttention.after.indexing", false)) {
              appIcon.requestAttention(myProject, false);
            }
            appIcon.setOkBadge(myProject, true);
          }
        });
      }
    }
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
     * Indicates that project has been just loaded and {@link StartupActivity.RequiredForSmartMode}-s were not executed to ensure project smart mode.
     */
    WAITING_PROJECT_SMART_MODE_STARTUP_TASKS
  }

  private class TrackedEdtActivity implements Runnable {
    private final @NotNull Runnable myRunnable;

    TrackedEdtActivity(@NotNull Runnable runnable) {
      myRunnable = runnable;
      myTrackedEdtActivities.add(this);
    }

    void invokeLater() {
      ApplicationManager.getApplication().invokeLater(this, getActivityExpirationCondition());
    }

    void invokeLaterIfProjectNotDisposed() {
      ApplicationManager.getApplication().invokeLater(this, getProjectActivityExpirationCondition());
    }

    void invokeLaterAfterProjectInitialized() {
      StartupManager startupManager = StartupManager.getInstance(myProject);
      startupManager.runWhenProjectIsInitialized((DumbAwareRunnable)() -> {
        Application app = ApplicationManager.getApplication();
        app.invokeLater(this, myDumbStartModality, getProjectActivityExpirationCondition());
      });
    }

    @Override
    public void run() {
      myTrackedEdtActivities.remove(this);
      myRunnable.run();
    }

    @SuppressWarnings({"RedundantCast", "unchecked", "rawtypes"})
    private @NotNull Condition getProjectActivityExpirationCondition() {
      return Conditions.or((Condition)myProject.getDisposed(), (Condition)getActivityExpirationCondition());
    }

    @NotNull
    Condition<?> getActivityExpirationCondition() {
      return __ -> !myTrackedEdtActivities.contains(this);
    }
  }

  private static boolean isSynchronousTaskExecution() {
    Application application = ApplicationManager.getApplication();
    return (application.isUnitTestMode() || application.isHeadlessEnvironment()) && !Boolean.parseBoolean(System.getProperty("idea.force.dumb.queue.tasks", "false"));
  }
}
