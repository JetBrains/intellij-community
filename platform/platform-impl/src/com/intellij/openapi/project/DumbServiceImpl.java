/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.project;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.AppIcon;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Queue;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Debugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class DumbServiceImpl extends DumbService implements Disposable, ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.DumbServiceImpl");
  private final AtomicReference<State> myState = new AtomicReference<>(State.SMART);
  private volatile Throwable myDumbStart;
  private volatile TransactionId myDumbStartTransaction;
  private final DumbModeListener myPublisher;
  private long myModificationCount;
  private final Set<Object> myQueuedEquivalences = new HashSet<>();
  private final Queue<DumbModeTask> myUpdatesQueue = new Queue<>(5);

  /**
   * Per-task progress indicators. Modified from EDT only.
   * The task is removed from this map after it's finished or when the project is disposed. 
   */
  private final Map<DumbModeTask, ProgressIndicatorEx> myProgresses = ContainerUtil.newConcurrentMap();
  
  private final Queue<Runnable> myRunWhenSmartQueue = new Queue<>(5);
  private final Project myProject;
  private final ThreadLocal<Integer> myAlternativeResolution = new ThreadLocal<>();
  private final StartupManager myStartupManager;

  public DumbServiceImpl(Project project, StartupManager startupManager) {
    myProject = project;
    myPublisher = project.getMessageBus().syncPublisher(DUMB_MODE);
    myStartupManager = startupManager;
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
    myRunWhenSmartQueue.clear();
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
  public void runWhenSmart(@NotNull Runnable runnable) {
    myStartupManager.runWhenProjectIsInitialized(() -> {
      synchronized (myRunWhenSmartQueue) {
        if (isDumb()) {
          myRunWhenSmartQueue.addLast(runnable);
          return;
        }
      }

      runnable.run();
    });
  }

  @Override
  public void queueTask(@NotNull DumbModeTask task) {
    if (LOG.isDebugEnabled()) LOG.debug("Scheduling task " + task);
    LOG.assertTrue(!myProject.isDefault(), "No indexing tasks should be created for default project: " + task);
    final Application application = ApplicationManager.getApplication();

    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      runTaskSynchronously(task);
    } else {
      queueAsynchronousTask(task);
    }
  }

  private static void runTaskSynchronously(@NotNull DumbModeTask task) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null) indicator = new EmptyProgressIndicator();

    indicator.pushState();
    try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing task")) {
      task.performInDumbMode(indicator);
    }
    finally {
      indicator.popState();
      Disposer.dispose(task);
    }
  }

  @VisibleForTesting
  void queueAsynchronousTask(@NotNull DumbModeTask task) {
    Throwable trace = new Throwable(); // please report exceptions here to peter
    TransactionId contextTransaction = TransactionGuard.getInstance().getContextTransaction();
    Runnable runnable = () -> queueTaskOnEdt(task, contextTransaction, trace);
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run(); // will log errors if not already in a write-safe context
    } else {
      TransactionGuard.submitTransaction(myProject, runnable);
    }
  }

  private void queueTaskOnEdt(@NotNull DumbModeTask task,
                              @Nullable TransactionId contextTransaction,
                              @NotNull Throwable trace) {
    if (!addTaskToQueue(task)) return;

    if (myState.get() == State.SMART || myState.get() == State.WAITING_FOR_FINISH) {
      enterDumbMode(contextTransaction, trace);
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

  private void enterDumbMode(@Nullable TransactionId contextTransaction, @NotNull Throwable trace) {
    boolean wasSmart = !isDumb();
    WriteAction.run(() -> {
      synchronized (myRunWhenSmartQueue) {
        myState.set(State.SCHEDULED_TASKS);
      }
      myDumbStart = trace;
      myDumbStartTransaction = contextTransaction;
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
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(
        () -> TransactionGuard.getInstance().submitTransaction(myProject, myDumbStartTransaction, this::updateFinished));
    }
  }

  private boolean switchToSmartMode() {
    synchronized (myRunWhenSmartQueue) {
      if (!myState.compareAndSet(State.WAITING_FOR_FINISH, State.SMART)) {
        return false;
      }
    }
    myDumbStart = null;
    myModificationCount++;
    return !myProject.isDisposed();
  }

  @Debugger.Insert(keyExpression = "runnable", group = "com.intellij.openapi.project.DumbService.runWhenSmart")
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
    }
  }

  @Override
  public void showDumbModeNotification(@NotNull final String message) {
    UIUtil.invokeLaterIfNeeded(() -> {
      final IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(myProject);
      if (ideFrame != null) {
        StatusBarEx statusBar = (StatusBarEx)ideFrame.getStatusBar();
        statusBar.notifyProgressByBalloon(MessageType.WARNING, message, null, null);
      }
    });
  }

  @Override
  public void waitForSmartMode() {
    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed() || application.isDispatchThread()) {
      throw new AssertionError("Don't invoke waitForSmartMode from inside read action in dumb mode");
    }

    while (isDumb() && !myProject.isDisposed()) {
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
    assert myProject.isInitialized();
    if (myState.get() != State.SCHEDULED_TASKS) {
      return;
    }

    while (isDumb()) {
      showModalProgress();
    }
  }

  private void showModalProgress() {
    NoAccessDuringPsiEvents.checkCallContext();
    try {
      ((ApplicationImpl)ApplicationManager.getApplication()).executeSuspendingWriteAction(myProject, IdeBundle.message("progress.indexing"), () ->
        runBackgroundProcess(ProgressManager.getInstance().getProgressIndicator()));
    }
    finally {
      if (myState.get() != State.SMART) {
        assertWeAreWaitingToFinish();
        updateFinished();
      }
    }
  }

  private void assertWeAreWaitingToFinish() {
    if (myState.get() != State.WAITING_FOR_FINISH) {
      Attachment[] attachments = myDumbStart != null ? new Attachment[]{new Attachment("indexingStart.trace", myDumbStart)} : Attachment.EMPTY_ARRAY;
      throw new RuntimeExceptionWithAttachments(myState.get().toString(), attachments);
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
    if (!myState.compareAndSet(State.SCHEDULED_TASKS, State.RUNNING_DUMB_TASKS)) return;

    ProgressSuspender.markSuspendable(visibleIndicator, "Indexing paused");

    final ShutDownTracker shutdownTracker = ShutDownTracker.getInstance();
    final Thread self = Thread.currentThread();
    try {
      shutdownTracker.registerStopperThread(self);

      if (visibleIndicator instanceof ProgressIndicatorEx) {
        ((ProgressIndicatorEx)visibleIndicator).addStateDelegate(new AppIconProgress());
      }

      DumbModeTask task = null;
      while (true) {
        Pair<DumbModeTask, ProgressIndicatorEx> pair = getNextTask(task, visibleIndicator);
        if (pair == null) break;

        task = pair.first;
        ProgressIndicatorEx taskIndicator = pair.second;
        if (visibleIndicator instanceof ProgressIndicatorEx) {
          taskIndicator.addStateDelegate(new AbstractProgressIndicatorExBase() {
            @Override
            protected void delegateProgressChange(@NotNull IndicatorAction action) {
              super.delegateProgressChange(action);
              action.execute((ProgressIndicatorEx)visibleIndicator);
            }
          });
        }
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
  private Pair<DumbModeTask, ProgressIndicatorEx> getNextTask(@Nullable DumbModeTask prevTask, @NotNull ProgressIndicator indicator) {
    CompletableFuture<Pair<DumbModeTask, ProgressIndicatorEx>> result = new CompletableFuture<>();
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myProject.isDisposed()) {
        result.completeExceptionally(new ProcessCanceledException());
        return;
      }

      if (prevTask != null) {
        Disposer.dispose(prevTask);
      }

      if (PowerSaveMode.isEnabled() && Registry.is("pause.indexing.in.power.save.mode")) {
        indicator.setText("Indexing paused during Power Save mode...");
        runWhenPowerSaveModeChanges(() -> result.complete(pollTaskQueue()));
        completeWhenProjectClosed(result);
      } else {
        result.complete(pollTaskQueue());
      }
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

  private void completeWhenProjectClosed(CompletableFuture<Pair<DumbModeTask, ProgressIndicatorEx>> result) {
    ProjectManagerListener listener = new ProjectManagerListener() {
      @Override
      public void projectClosed(Project project) {
        result.completeExceptionally(new ProcessCanceledException());
      }
    };
    ProjectManager.getInstance().addProjectManagerListener(myProject, listener);
    result.thenAccept(p -> ProjectManager.getInstance().removeProjectManagerListener(myProject, listener));
  }

  private void runWhenPowerSaveModeChanges(Runnable r) {
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(PowerSaveMode.TOPIC, () -> {
      r.run();
      connection.disconnect();
    });
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
            appIcon.requestAttention(myProject, false);
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
     * In this state, it's possible to call {@link #completeJustSubmittedTasks()} and perform all submitted the tasks modally.
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
