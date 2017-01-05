/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.*;
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
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class DumbServiceImpl extends DumbService implements Disposable, ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.DumbServiceImpl");
  private static Throwable ourForcedTrace;
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

  public DumbServiceImpl(Project project) {
    myProject = project;
    myPublisher = project.getMessageBus().syncPublisher(DUMB_MODE);
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
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> {
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
  public void queueTask(@NotNull final DumbModeTask task) {
    TransactionId contextTransaction = TransactionGuard.getInstance().getContextTransaction();

    final Throwable trace = ourForcedTrace != null ? ourForcedTrace : new Throwable(); // please report exceptions here to peter
    if (LOG.isDebugEnabled()) LOG.debug("Scheduling task " + task);
    final Application application = ApplicationManager.getApplication();

    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.pushState();
      }
      AccessToken token = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing task");
      try {
        task.performInDumbMode(indicator != null ? indicator : new EmptyProgressIndicator());
      }
      finally {
        token.finish();
        if (indicator != null) {
          indicator.popState();
        }
        Disposer.dispose(task);
      }
      return;
    }

    Runnable runnable = () -> queueTaskOnEdt(task, contextTransaction, trace);
    if (application.isDispatchThread()) {
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
      WriteAction.run(() -> enterDumbMode(contextTransaction, trace));
      ApplicationManager.getApplication().invokeLater(this::startBackgroundProcess);
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
    synchronized (myRunWhenSmartQueue) {
      myState.set(State.SCHEDULED_TASKS);
    }
    myDumbStart = trace;
    myDumbStartTransaction = contextTransaction;
    myModificationCount++;
    if (wasSmart) {
      try {
        myPublisher.enteredDumbMode();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public static AccessToken forceDumbModeStartTrace(@NotNull Throwable trace) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final Throwable prev = ourForcedTrace;
    ourForcedTrace = trace;
    return new AccessToken() {
      @Override
      public void finish() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourForcedTrace = prev;
      }
    };
  }

  private void queueUpdateFinished() {
    if (myState.compareAndSet(State.RUNNING_DUMB_TASKS, State.WAITING_FOR_FINISH)) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(
        () -> TransactionGuard.getInstance().submitTransaction(myProject, myDumbStartTransaction, () ->
          WriteAction.run(
            () -> updateFinished())));
    }
  }

  private void updateFinished() {
    synchronized (myRunWhenSmartQueue) {
      if (!myState.compareAndSet(State.WAITING_FOR_FINISH, State.SMART)) {
        return;
      }
    }
    myDumbStart = null;
    myModificationCount++;
    if (myProject.isDisposed()) return;

    if (ApplicationManager.getApplication().isInternal()) LOG.info("updateFinished");

    notifyUpdateFinished();
  }

  private void notifyUpdateFinished() {
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
          LOG.error("Task canceled: " + runnable, new Attachment("pce.trace", ExceptionUtil.getThrowableText(e)));
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
    if (!isDumb()) {
      return;
    }

    final Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed() || application.isDispatchThread()) {
      throw new AssertionError("Don't invoke waitForSmartMode from inside read action in dumb mode");
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    runWhenSmart(semaphore::up);
    while (true) {
      if (semaphore.waitFor(50)) {
        return;
      }
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
        if (myState.get() != State.WAITING_FOR_FINISH) throw new AssertionError(myState.get());
        WriteAction.run(() -> updateFinished());
      }
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

    final ShutDownTracker shutdownTracker = ShutDownTracker.getInstance();
    final Thread self = Thread.currentThread();
    try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing tasks")) {
      shutdownTracker.registerStopperThread(self);

      if (visibleIndicator instanceof ProgressIndicatorEx) {
        ((ProgressIndicatorEx)visibleIndicator).addStateDelegate(new AppIconProgress());
      }

      DumbModeTask task = null;
      while (true) {
        Pair<DumbModeTask, ProgressIndicatorEx> pair = getNextTask(task);
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
        runSingleTask(task, taskIndicator);
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

  @Nullable private Pair<DumbModeTask, ProgressIndicatorEx> getNextTask(@Nullable final DumbModeTask prevTask) {
    final Ref<Pair<DumbModeTask, ProgressIndicatorEx>> result = Ref.create();
    invokeAndWaitIfNeeded(() -> {
      if (myProject.isDisposed()) return;
      if (prevTask != null) {
        Disposer.dispose(prevTask);
      }

      while (true) {
        if (myUpdatesQueue.isEmpty()) {
          queueUpdateFinished();
          return;
        }

        DumbModeTask queuedTask = myUpdatesQueue.pullFirst();
        myQueuedEquivalences.remove(queuedTask.getEquivalenceObject());
        ProgressIndicatorEx indicator = myProgresses.get(queuedTask);
        if (indicator.isCanceled()) {
          Disposer.dispose(queuedTask);
          continue;
        }

        result.set(Pair.create(queuedTask, indicator));
        return;
      }
    });
    return result.get();
  }

  private static void invokeAndWaitIfNeeded(Runnable runnable) {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
      return;
    }

    Semaphore semaphore = new Semaphore();
    semaphore.down();
    app.invokeLater(() -> {
      try {
        runnable.run();
      } finally {
        semaphore.up();
      }
    }, ModalityState.any());
    try {
      semaphore.waitFor();
    }
    catch (ProcessCanceledException ignore) {
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
