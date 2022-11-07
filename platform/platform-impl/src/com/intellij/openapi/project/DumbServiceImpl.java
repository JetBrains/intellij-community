// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.codeWithMe.ClientId;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
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
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NlsContexts.PopupContent;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.ui.DeprecationStripePanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

@ApiStatus.Internal
public class DumbServiceImpl extends DumbService implements Disposable, ModificationTracker, DumbServiceBalloon.Service {
  private static final ExtensionPointName<StartupActivity.RequiredForSmartMode> REQUIRED_FOR_SMART_MODE_STARTUP_ACTIVITY
    = new ExtensionPointName<>("com.intellij.requiredForSmartModeStartupActivity");

  public static final boolean ALWAYS_SMART = SystemProperties.getBooleanProperty("idea.no.dumb.mode", false);

  private static final Logger LOG = Logger.getInstance(DumbServiceImpl.class);
  private final AtomicReference<State> myState;
  private volatile Throwable myDumbEnterTrace;
  private volatile Throwable myDumbStart;
  private final DumbModeListener myPublisher;
  private long myModificationCount;

  private final Deque<Runnable> myRunWhenSmartQueue = new ArrayDeque<>(5);
  private final Project myProject;

  private final TrackedEdtActivityService myTrackedEdtActivityService;
  private final DumbServiceMergingTaskQueue myTaskQueue;
  private final DumbServiceGuiTaskQueue myGuiDumbTaskRunner;
  private final DumbServiceSyncTaskQueue mySyncDumbTaskRunner;
  private final DumbServiceHeavyActivities myHeavyActivities;
  private final DumbServiceAlternativeResolveTracker myAlternativeResolveTracker;

  //used from EDT
  private final DumbServiceBalloon myBalloon;

  private volatile @Nullable Thread myWaitIntolerantThread;

  private class DumbTaskListener implements DumbServiceGuiTaskQueue.DumbTaskListener {
    /*
     * beforeFirstTask and afterLastTask always follow one after another. Receiving several beforeFirstTask or afterLastTask in row is
     * always a failure of DumbServiceGuiTaskQueue
     */
    @Override
    public void beforeFirstTask() {
      // FIXME: this is not correct: if a queue has already been emptied by modal dumb progress, we'll be in state SMART, not SCHEDULED_TASKS
      assertState(State.SCHEDULED_TASKS);
      myState.set(State.RUNNING_DUMB_TASKS);
    }

    @Override
    public void afterLastTask() {
      assertState(State.RUNNING_DUMB_TASKS);
      boolean changed = myState.compareAndSet(State.RUNNING_DUMB_TASKS, State.WAITING_FOR_FINISH);
      LOG.assertTrue(changed, "Failed to change state: RUNNING_DUMB_TASKS>WAITING_FOR_FINISH. Current state: " + myState.get());

      if (myTaskQueue.isEmpty()) {
        myTrackedEdtActivityService.invokeLaterAfterProjectInitialized(DumbServiceImpl.this::updateFinished);
      }
      else {
        boolean needToScheduleNow = myState.compareAndSet(State.WAITING_FOR_FINISH, State.SCHEDULED_TASKS);
        if (needToScheduleNow) {
          // it is myGuiDumbTaskRunner's responsibility to ignore redundant startBackgroundProcess calls.
          // queueTaskOnEdt does not use CAS to change state WAITING_FOR_FINISH > SCHEDULED_TASKS, so we can call startBackgroundProcess several times.
          myTrackedEdtActivityService.invokeLaterIfProjectNotDisposed(myGuiDumbTaskRunner::startBackgroundProcess);
        }
      }
    }
  }

  public DumbServiceImpl(@NotNull Project project) {
    myProject = project;
    myTrackedEdtActivityService = new TrackedEdtActivityService(project);
    myTaskQueue = new DumbServiceMergingTaskQueue();
    myHeavyActivities = new DumbServiceHeavyActivities();
    myGuiDumbTaskRunner = new DumbServiceGuiTaskQueue(myProject, myTaskQueue, myHeavyActivities, new DumbTaskListener());
    mySyncDumbTaskRunner = new DumbServiceSyncTaskQueue(myTaskQueue);

    myPublisher = project.getMessageBus().syncPublisher(DUMB_MODE);

    new DumbServiceVfsBatchListener(myProject, myHeavyActivities);
    myBalloon = new DumbServiceBalloon(project, this);
    myAlternativeResolveTracker = new DumbServiceAlternativeResolveTracker();
    myState = new AtomicReference<>(project.isDefault() ? State.SMART : State.WAITING_PROJECT_SMART_MODE_STARTUP_TASKS);

    project.getMessageBus().simpleConnect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        myRunWhenSmartQueue.removeIf(runnable -> {
          if (runnable instanceof RunnableDelegate) {
            runnable = ((RunnableDelegate)runnable).task;
          }
          ClassLoader classLoader = runnable.getClass().getClassLoader();
          return classLoader instanceof PluginAwareClassLoader &&
                 ((PluginAwareClassLoader)classLoader).getPluginId().equals(pluginDescriptor.getPluginId());
        });
      }
    });
  }

  private static final class RunnableDelegate implements Runnable {
    final Runnable task;
    private final @NotNull Consumer<? super Runnable> executor;

    private RunnableDelegate(@NotNull Runnable task, @NotNull Consumer<? super Runnable> executor) {
      this.task = task;
      this.executor = executor;
    }

    @Override
    public void run() {
      executor.accept(task);
    }
  }

  void queueStartupActivitiesRequiredForSmartMode() {
    boolean changed = myState.compareAndSet(State.WAITING_PROJECT_SMART_MODE_STARTUP_TASKS, State.RUNNING_PROJECT_SMART_MODE_STARTUP_TASKS);
    LOG.assertTrue(changed, "actual state: " + myState.get() + ", project " + getProject());

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
    myHeavyActivities.disposeSuspender();
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
  public void setAlternativeResolveEnabled(boolean enabled) {
    myAlternativeResolveTracker.setAlternativeResolveEnabled(enabled);
  }

  @Override
  public ModificationTracker getModificationTracker() {
    return this;
  }

  @Override
  public boolean isDumb() {
    if (ALWAYS_SMART) return true;
    if (!ApplicationManager.getApplication().isReadAccessAllowed() &&
        Registry.is("ide.check.is.dumb.contract")) {
      LOG.error("To avoid race conditions isDumb method should be used only under read action or in EDT thread.",
                           new IllegalStateException());
    }
    return myState.get() != State.SMART;
  }

  @TestOnly
  public void setDumb(boolean dumb) {
    ApplicationManager.getApplication().assertIsDispatchThread();
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
    StartupManager.getInstance(myProject).runAfterOpened(() -> doUnsafeRunWhenSmart(runnable));
  }

  private void doUnsafeRunWhenSmart(@NotNull Runnable runnable) {
    if (!ALWAYS_SMART) {
      synchronized (myRunWhenSmartQueue) {
        if (isDumb()) {
          Runnable executor = ClientId.decorateRunnable(runnable);
          myRunWhenSmartQueue.addLast(executor == runnable ? runnable : new RunnableDelegate(runnable, it -> executor.run()));
          return;
        }
      }
    }

    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(() -> doUnsafeRunWhenSmart(runnable), ModalityState.NON_MODAL, myProject.getDisposed());
    }
  }

  @Override
  public void unsafeRunWhenSmart(@NotNull @Async.Schedule Runnable runnable) {
    if (!ALWAYS_SMART) {
      synchronized (myRunWhenSmartQueue) {
        if (isDumb()) {
          myRunWhenSmartQueue.addLast(ClientId.decorateRunnable(runnable));
          return;
        }
      }
    }

    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(() -> unsafeRunWhenSmart(runnable), myProject.getDisposed());
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
      myTrackedEdtActivityService.submitTransaction(runnable);
    }
  }

  private void queueTaskOnEdt(@NotNull DumbModeTask task, @NotNull ModalityState modality, @NotNull Throwable trace) {
    myTaskQueue.addTask(task);
    State state = myState.get();
    if (state == State.SMART ||
        state == State.WAITING_FOR_FINISH ||
        state == State.RUNNING_PROJECT_SMART_MODE_STARTUP_TASKS) {
      enterDumbMode(modality, trace);
      // we need one more invoke later because we want to invoke LATER. I.e. right now one can invoke completeJustSubmittedTasks and
      // drain the queue synchronously under modal progress
      myTrackedEdtActivityService.invokeLaterIfProjectNotDisposed(myGuiDumbTaskRunner::startBackgroundProcess);
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

  private boolean switchToSmartMode() {
    synchronized (myRunWhenSmartQueue) {
      if (!myState.compareAndSet(State.WAITING_FOR_FINISH, State.SMART)) {
        return false;
      }
    }

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
          runnable = myRunWhenSmartQueue.pollFirst();
          if (runnable == null) {
            break;
          }
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
    if (!application.isWriteThread() || application.isWriteAccessAllowed()) {
      throw new AssertionError("Must be called on write thread without write action");
    }

    Thread currentThread = Thread.currentThread();
    String initialThreadName = currentThread.getName();
    while (!(myState.get() == State.SMART ||
             myState.get() == State.WAITING_PROJECT_SMART_MODE_STARTUP_TASKS ||
             myState.get() == State.WAITING_FOR_FINISH)
           && !myProject.isDisposed()) {
      ConcurrencyUtil.runUnderThreadName(initialThreadName + " [DumbService.cancelAllTasksAndWait(state = " + myState.get() + ")]", () -> {
        PingProgress.interactWithEdtProgress();
        LockSupport.parkNanos(50_000_000);
        // polls next dumb mode task
        myTrackedEdtActivityService.executeAllQueuedActivities();
        // cancels all scheduled and running tasks
        myTaskQueue.cancelAllTasks();
        myHeavyActivities.resumeProgressIfPossible();
      });
    }
  }

  @Override
  public void waitForSmartMode() {
    if (ALWAYS_SMART) return;
    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed()) {
      throw new AssertionError("Don't invoke waitForSmartMode from inside read action in dumb mode");
    }
    if (myWaitIntolerantThread == Thread.currentThread()) {
      throw new AssertionError("Don't invoke waitForSmartMode from a background startup activity");
    }
    CountDownLatch switched;
    synchronized (myRunWhenSmartQueue) {
      if (!isDumb()) {
        return;
      }
      switched = new CountDownLatch(1);
      myRunWhenSmartQueue.addLast(switched::countDown);
    }

    while (myState.get() != State.SMART && !myProject.isDisposed()) {
      try {
        switched.await(50, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException ignored) { }
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
      ((ApplicationImpl)ApplicationManager.getApplication()).executeSuspendingWriteAction(myProject, IndexingBundle.message("progress.indexing.title"), () -> {
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

  private void assertState(@NotNull State @NotNull ... expected) {
    State state = myState.get();
    if (!ArrayUtil.contains(state, expected)) {
      List<Attachment> attachments = new ArrayList<>();
      if (myDumbEnterTrace != null) {
        attachments.add(new Attachment("indexingStart", myDumbEnterTrace));
      }
      attachments.add(new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString()));
      throw new RuntimeExceptionWithAttachments("Internal error, please include thread dump attachment. " +
                                                "Expected " + Arrays.asList(expected) + ", but was " + state.toString(),
                                                attachments.toArray(Attachment.EMPTY_ARRAY));
    }
  }

  private void runBackgroundProcess(final @NotNull ProgressIndicator visibleIndicator) {
    myGuiDumbTaskRunner.runBackgroundProcess(visibleIndicator);
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

  private enum State {
    /** Non-dumb mode. For all other states, {@link #isDumb()} returns {@code true}. */
    SMART,

    /**
     * A state between entering dumb mode ({@link #queueTaskOnEdt}) and actually starting the background progress later ({@link #runBackgroundProcess}).
     * In this state, it's possible to call {@link #completeJustSubmittedTasks()} and perform all the submitted tasks synchronously.
     * This state can happen after {@link #SMART}, {@link #WAITING_FOR_FINISH}, or {@link #RUNNING_PROJECT_SMART_MODE_STARTUP_TASKS}.
     * Followed by {@link #RUNNING_DUMB_TASKS}.
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

  public static boolean isSynchronousTaskExecution() {
    Application application = ApplicationManager.getApplication();
    return (application.isUnitTestMode() || application.isHeadlessEnvironment()) && !Boolean.parseBoolean(System.getProperty("idea.force.dumb.queue.tasks", "false"));
  }
}
