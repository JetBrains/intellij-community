// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.internal.statistic.IdeActivity;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.UIEventId;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.LaterInvocator;
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
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.AppIcon;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Queue;
import com.intellij.util.exception.FrequentErrorLogger;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class DumbServiceImpl extends DumbService implements Disposable, ModificationTracker {
  private static final Logger LOG = Logger.getInstance(DumbServiceImpl.class);
  private static final FrequentErrorLogger ourErrorLogger = FrequentErrorLogger.newInstance(LOG);
  private final AtomicReference<State> myState = new AtomicReference<>(State.SMART);
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
  private final Map<DumbModeTask, ProgressIndicatorEx> myProgresses = ContainerUtil.newConcurrentMap();
  private Pair<DumbModeTask, ProgressIndicatorEx> myCurrentProgress; //used from EDT only
  private volatile ProgressIndicatorEx myDialogIndicator; //used from EDT only
  private boolean myDialogRequested = false;//used from EDT only

  private final Queue<Runnable> myRunWhenSmartQueue = new Queue<>(5);
  private final Project myProject;
  private final ThreadLocal<Integer> myAlternativeResolution = new ThreadLocal<>();
  private volatile ProgressSuspender myCurrentSuspender;
  private final List<String> myRequestedSuspensions = ContainerUtil.createEmptyCOWList();

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
    ApplicationManager.getApplication().assertIsDispatchThread();
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

  @NotNull
  private AccessToken heavyActivityStarted(@NotNull String activityName) {
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

  @Override
  public void runWhenSmart(@Async.Schedule @NotNull Runnable runnable) {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> unsafeRunWhenSmart(runnable));
  }

  @Override
  public void unsafeRunWhenSmart(@NotNull @Async.Schedule Runnable runnable) {
    synchronized (myRunWhenSmartQueue) {
      if (isDumb()) {
        myRunWhenSmartQueue.addLast(runnable);
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

    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
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
    try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing task")) {
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

    if (myState.get() == State.SMART || myState.get() == State.WAITING_FOR_FINISH) {
      enterDumbMode(modality, trace);
      ApplicationManager.getApplication().invokeLater(this::startBackgroundProcess, myProject.getDisposed());
    }
  }

  private boolean addTaskToQueue(@NotNull DumbModeTask task) {
    if (!myQueuedEquivalences.add(task.getEquivalenceObject())) {
      Disposer.dispose(task);
      return false;
    }

    myProgresses.put(task, new ProgressIndicatorBase());
    Disposer.register(task, () -> {
      ApplicationManager.getApplication().assertIsDispatchThread();
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
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(
        () -> ApplicationManager.getApplication().invokeLater(this::updateFinished, myDumbStartModality, myProject.getDisposed()));
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
  public void showDumbModeNotification(@NotNull final String message) {
    UIUtil.invokeLaterIfNeeded(() -> {
      final IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(myProject);
      if (ideFrame != null) {
        ((StatusBarEx)ideFrame.getStatusBar()).notifyProgressByBalloon(MessageType.WARNING, message);
      }
    });
  }

  @Override
  public boolean showDumbModeDialog(@NotNull List<String> actionNames) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!isDumb()) {
      UIEventLogger.logUIEvent(UIEventId.DumbModeDialogWasNotNeeded, new FeatureUsageData().addProject(myProject));
      return true;
    }
    if (myDialogRequested) {
      //here should be an assert that it does not happen, but now we have two dispatches of one InputEvent, see IDEA-227444
      return false;
    }
    UIEventLogger.logUIEvent(UIEventId.DumbModeDialogRequested, new FeatureUsageData().addProject(myProject));
    long progressesStartTimestamp = System.currentTimeMillis();
    int iteration = 0;
    while (isDumb()) {
      if (tryShowDialogTillSmartMode(actionNames, iteration)) return false;
      iteration++;
    }
    FeatureUsageData data = new FeatureUsageData().addProject(myProject).
      addData("duration_ms", System.currentTimeMillis() - progressesStartTimestamp);
    UIEventLogger.logUIEvent(UIEventId.DumbModeDialogProceededToActions, data);
    return true;
  }

  /**
   * @return true if the progress was cancelled
   */
  private boolean tryShowDialogTillSmartMode(@NotNull List<String> actionNames, int iteration) {
    long startTimestamp = System.currentTimeMillis();
    UIEventLogger.logUIEvent(UIEventId.DumbModeDialogShown, new FeatureUsageData().addProject(myProject).addCount(iteration));
    myDialogRequested = true;
    AtomicBoolean dialogCancelled = new AtomicBoolean(false);
    Semaphore semaphore = new Semaphore(1);
    runWhenSmart(semaphore::up);
    Task.Modal task = new Task.Modal(myProject, createDialogTitle(actionNames), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        LaterInvocator.markTransparent(indicator.getModalityState());
        indicator.setText("Waiting for indexing to finish...");
        ApplicationManager.getApplication().invokeAndWait(() -> {
          myDialogIndicator = (ProgressIndicatorEx)indicator;
          attachDialogIndicatorToCurrentProgress();
        });
        try {
          ProgressIndicatorUtils.awaitWithCheckCanceled(semaphore, indicator);
        }
        catch (ProcessCanceledException e) {
          dialogCancelled.set(true);
        }
      }
    };
    task.setCancelText(createCancelButtonText(actionNames));
    ProgressManager.getInstance().run(task);
    myDialogIndicator = null;
    myDialogRequested = false;
    FeatureUsageData data = new FeatureUsageData().addProject(myProject).addCount(iteration).
      addData("duration_ms", System.currentTimeMillis() - startTimestamp);
    UIEventLogger.logUIEvent(dialogCancelled.get() ? UIEventId.DumbModeDialogCancelled : UIEventId.DumbModeDialogFinished, data);
    return dialogCancelled.get();
  }

  @NotNull
  private static String createDialogTitle(@NotNull List<String> actionNames) {
    if (actionNames.isEmpty()) {
      return "Action";
    }
    else if (actionNames.size() == 1) {
      return "'" + actionNames.get(0) + "' Action";
    }
    else {
      return "Actions: " + StringUtil.join(actionNames, ", ");
    }
  }

  @NotNull
  private static String createCancelButtonText(@NotNull List<String> actionNames) {
    if (actionNames.isEmpty()) {
      return "Discard action";
    }
    else if (actionNames.size() == 1) {
      return "Discard '" + actionNames.get(0) + "'";
    }
    else {
      return "Discard actions";
    }
  }

  private void attachDialogIndicatorToCurrentProgress() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ProgressIndicatorEx dialogIndicator = myDialogIndicator;
    if (dialogIndicator == null || myCurrentProgress == null) return;

    AbstractProgressIndicatorExBase delegatingVisibleStateIndicator = new AbstractProgressIndicatorExBase() {
      @Override
      public void setText(String text) {
      }

      @Override
      protected void delegateProgressChange(@NotNull IndicatorAction action) {
        super.delegateProgressChange(action);
        action.execute(dialogIndicator);
      }
    };

    String text = dialogIndicator.getText();
    myCurrentProgress.second.addStateDelegate(delegatingVisibleStateIndicator);
    dialogIndicator.setText(text);
    dialogIndicator.setIndeterminate(delegatingVisibleStateIndicator.isIndeterminate());
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
  public void smartInvokeLater(@NotNull final Runnable runnable) {
    smartInvokeLater(runnable, ModalityState.defaultModalityState());
  }

  @Override
  public void smartInvokeLater(@NotNull final Runnable runnable, @NotNull ModalityState modalityState) {
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
    ApplicationManager.getApplication().assertIsDispatchThread();
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
    NoAccessDuringPsiEvents.checkCallContext();
    try {
      ((ApplicationImpl)ApplicationManager.getApplication()).executeSuspendingWriteAction(myProject, IdeBundle.message("progress.indexing"), () -> {
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
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, IdeBundle.message("progress.indexing"), false) {
        @Override
        public void run(@NotNull final ProgressIndicator visibleIndicator) {
          runBackgroundProcess(visibleIndicator);
        }
      });
    }
    catch (Throwable e) {
      queueUpdateFinished();
      LOG.error("Failed to start background index update task", e);
    }
  }

  private void runBackgroundProcess(@NotNull final ProgressIndicator visibleIndicator) {
    ((ProgressManagerImpl)ProgressManager.getInstance()).markProgressSafe((ProgressWindow)visibleIndicator);

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
          try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing tasks")) {
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
        taskIndicator.setText(IdeBundle.message("progress.indexing.scanning"));

        task.performInDumbMode(taskIndicator);
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (Throwable unexpected) {
        LOG.error(unexpected);
      }
    }, taskIndicator);
  }

  @Nullable
  private Pair<DumbModeTask, ProgressIndicatorEx> getNextTask(@Nullable DumbModeTask prevTask) {
    CompletableFuture<Pair<DumbModeTask, ProgressIndicatorEx>> result = new CompletableFuture<>();
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myProject.isDisposed()) {
        result.completeExceptionally(new ProcessCanceledException());
        return;
      }

      if (prevTask != null) {
        Disposer.dispose(prevTask);
      }

      Pair<DumbModeTask, ProgressIndicatorEx> value = pollTaskQueue();
      myCurrentProgress = value;
      attachDialogIndicatorToCurrentProgress();
      result.complete(value);
    });
    return waitForFuture(result);
  }

  @Nullable
  private Pair<DumbModeTask, ProgressIndicatorEx> pollTaskQueue() {
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

  @Nullable
  private static <T> T waitForFuture(Future<T> result) {
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

  @Nullable
  public Throwable getDumbModeStartTrace() {
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
    WAITING_FOR_FINISH
  }
}
