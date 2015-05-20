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
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.AppIcon;
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
import java.util.Map;

public class DumbServiceImpl extends DumbService implements Disposable, ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.DumbServiceImpl");
  private volatile boolean myDumb = false;
  private final DumbModeListener myPublisher;
  private long myModificationCount;
  private final Queue<DumbModeTask> myUpdatesQueue = new Queue<DumbModeTask>(5);

  /**
   * Per-task progress indicators. Modified from EDT only.
   * The task is removed from this map after it's finished or when the project is disposed. 
   */
  private final Map<DumbModeTask, ProgressIndicatorEx> myProgresses = ContainerUtil.newConcurrentMap();
  
  private final Queue<Runnable> myRunWhenSmartQueue = new Queue<Runnable>(5);
  private final Project myProject;
  private final ThreadLocal<Integer> myAlternativeResolution = new ThreadLocal<Integer>();

  public DumbServiceImpl(Project project) {
    myProject = project;
    myPublisher = project.getMessageBus().syncPublisher(DUMB_MODE);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static DumbServiceImpl getInstance(@NotNull Project project) {
    return (DumbServiceImpl)DumbService.getInstance(project);
  }

  @Override
  public void queueTask(@NotNull final DumbModeTask task) {
    scheduleCacheUpdate(task, true);
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
    myRunWhenSmartQueue.clear();
    for (DumbModeTask task : new ArrayList<DumbModeTask>(myProgresses.keySet())) {
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
    return myDumb;
  }

  @TestOnly
  public void setDumb(boolean dumb) {
    if (dumb) {
      myDumb = true;
      myPublisher.enteredDumbMode();
    }
    else {
      updateFinished();
    }
  }

  @Override
  public void runWhenSmart(@NotNull Runnable runnable) {
    if (!isDumb()) {
      runnable.run();
    }
    else {
      synchronized (myRunWhenSmartQueue) {
        myRunWhenSmartQueue.addLast(runnable);
      }
    }
  }

  private void scheduleCacheUpdate(@NotNull final DumbModeTask task, boolean forceDumbMode) {
    if (LOG.isDebugEnabled()) LOG.debug("Scheduling task " + task, new Throwable());
    final Application application = ApplicationManager.getApplication();

    if (application.isUnitTestMode() ||
        application.isHeadlessEnvironment() ||
        !forceDumbMode && !myDumb && application.isReadAccessAllowed()) {
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

    UIUtil.invokeLaterIfNeeded(new DumbAwareRunnable() {
      @Override
      public void run() {
        if (myProject.isDisposed()) {
          return;
        }
        final ProgressIndicatorBase indicator = new ProgressIndicatorBase() {
          @Override
          protected void delegateRunningChange(@NotNull AbstractProgressIndicatorExBase.IndicatorAction action) {
            // don't delegate lifecycle events to the global indicator as several independent tasks may run under it sequentially
          }
        };
        myProgresses.put(task, indicator);
        Disposer.register(task, new Disposable() {
          @Override
          public void dispose() {
            application.assertIsDispatchThread();
            myProgresses.remove(task);
          }
        });
        myUpdatesQueue.addLast(task);
        // ok to test and set the flag like this, because the change is always done from dispatch thread
        if (!myDumb) {
          // always change dumb status inside write action.
          // This will ensure all active read actions are completed before the app goes dumb
          boolean startSuccess =
            application.runWriteAction(new Computable<Boolean>() {
              @Override
              public Boolean compute() {
                myDumb = true;
                myModificationCount++;
                try {
                  myPublisher.enteredDumbMode();
                }
                catch (Throwable e) {
                  LOG.error(e);
                }

                try {
                  startBackgroundProcess();
                }
                catch (Throwable e) {
                  LOG.error("Failed to start background index update task", e);
                  return false;
                }
                return true;
              }
            });
          if (!startSuccess) {
            updateFinished();
          }
        }
      }
    });
  }

  private void updateFinished() {
    myDumb = false;
    myModificationCount++;
    if (myProject.isDisposed()) return;

    if (ApplicationManager.getApplication().isInternal()) LOG.info("updateFinished");

    try {
      myPublisher.exitDumbMode();
      FileEditorManagerEx.getInstanceEx(myProject).refreshIcons();
    }
    finally {
      // It may happen that one of the pending runWhenSmart actions triggers new dumb mode;
      // in this case we should quit processing pending actions and postpone them until the newly started dumb mode finishes.
      while (!myDumb) {
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
        catch (Throwable e) {
          LOG.error("Error executing task " + runnable, e);
        }
      }
    }
  }

  @Override
  public void showDumbModeNotification(@NotNull final String message) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        final IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(myProject);
        if (ideFrame != null) {
          StatusBarEx statusBar = (StatusBarEx)ideFrame.getStatusBar();
          statusBar.notifyProgressByBalloon(MessageType.WARNING, message, null, null);
        }
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
    runWhenSmart(new Runnable() {
      @Override
      public void run() {
        semaphore.up();
      }
    });
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
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        runWhenSmart(runnable);
      }

      @Override
      public String toString() {
        return runnable.toString();
      }
    }, myProject.getDisposed());
  }

  @Override
  public void smartInvokeLater(@NotNull final Runnable runnable, @NotNull ModalityState modalityState) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        runWhenSmart(runnable);
      }
    }, modalityState, myProject.getDisposed());
  }

  private void startBackgroundProcess() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, IdeBundle.message("progress.indexing"), false) {

      @Override
      public void run(@NotNull final ProgressIndicator visibleIndicator) {
        final ShutDownTracker shutdownTracker = ShutDownTracker.getInstance();
        final Thread self = Thread.currentThread();
        AccessToken token = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing tasks");
        try {
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
              taskIndicator.addStateDelegate((ProgressIndicatorEx)visibleIndicator);
            }
            runSingleTask(task, taskIndicator);
          }
        }
        catch (Throwable unexpected) {
          LOG.error(unexpected);
        }
        finally {
          shutdownTracker.unregisterStopperThread(self);
          token.finish();
        }
      }
    });
  }

  private static void runSingleTask(final DumbModeTask task, final ProgressIndicatorEx taskIndicator) {
    if (ApplicationManager.getApplication().isInternal()) LOG.info("Running dumb mode task: " + task);
    
    // nested runProcess is needed for taskIndicator to be honored in ProgressManager.checkCanceled calls deep inside tasks 
    ProgressManager.getInstance().runProcess(new Runnable() {
      @Override
      public void run() {
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
      }
    }, taskIndicator);
  }

  @Nullable private Pair<DumbModeTask, ProgressIndicatorEx> getNextTask(@Nullable final DumbModeTask prevTask) {
    final Ref<Pair<DumbModeTask, ProgressIndicatorEx>> result = Ref.create();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (myProject.isDisposed()) return;
        if (prevTask != null) {
          Disposer.dispose(prevTask);
        }

        while (true) {
          if (myUpdatesQueue.isEmpty()) {
            updateFinished();
            return;
          }

          DumbModeTask queuedTask = myUpdatesQueue.pullFirst();
          ProgressIndicatorEx indicator = myProgresses.get(queuedTask);
          if (indicator.isCanceled()) {
            Disposer.dispose(queuedTask);
            continue;
          }
          
          result.set(Pair.create(queuedTask, indicator));
          return;
        }
      }
    });
    return result.get();
  }

  @Override
  public long getModificationCount() {
    return myModificationCount;
  }

  private class AppIconProgress extends ProgressIndicatorBase {
    private double lastFraction;

    @Override
    public void setFraction(final double fraction) {
      if (fraction - lastFraction < 0.01d) return;
      lastFraction = fraction;
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          AppIcon.getInstance().setProgress(myProject, "indexUpdate", AppIconScheme.Progress.INDEXING, fraction, true);
        }
      });
    }

    @Override
    public void finish(@NotNull TaskInfo task) {
      if (lastFraction != 0) { // we should call setProgress at least once before
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            AppIcon appIcon = AppIcon.getInstance();
            if (appIcon.hideProgress(myProject, "indexUpdate")) {
              appIcon.requestAttention(myProject, false);
              appIcon.setOkBadge(myProject, true);
            }
          }
        });
      }
    }
  }
}
