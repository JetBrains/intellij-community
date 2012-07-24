/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.wm.AppIconScheme;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.ui.AppIcon;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.Queue;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DumbServiceImpl extends DumbService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.DumbServiceImpl");
  private volatile boolean myDumb = false;
  private final DumbModeListener myPublisher;
  private final Queue<IndexUpdateRunnable> myUpdatesQueue = new Queue<IndexUpdateRunnable>(5);
  private final Queue<Runnable> myRunWhenSmartQueue = new Queue<Runnable>(5);
  private final Project myProject;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static DumbServiceImpl getInstance(@NotNull Project project) {
    return (DumbServiceImpl)DumbService.getInstance(project);
  }

  public DumbServiceImpl(Project project, MessageBus bus) {
    myProject = project;
    myPublisher = bus.syncPublisher(DUMB_MODE);
  }

  @Override
  public Project getProject() {
    return myProject;
  }

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
  public void runWhenSmart(Runnable runnable) {
    if (!isDumb()) {
      runnable.run();
    }
    else {
      synchronized (myRunWhenSmartQueue) {
        myRunWhenSmartQueue.addLast(runnable);
      }
    }
  }

  public void queueCacheUpdate(Collection<CacheUpdater> updaters) {
    scheduleCacheUpdate(updaters, false);
  }

  public void queueCacheUpdateInDumbMode(Collection<CacheUpdater> updaters) {
    scheduleCacheUpdate(updaters, true);
  }

  private void scheduleCacheUpdate(Collection<CacheUpdater> updaters, boolean forceDumbMode) {
    // prevent concurrent modifications
    final CacheUpdateRunner runner = new CacheUpdateRunner(myProject, new ArrayList<CacheUpdater>(updaters));

    final Application application = ApplicationManager.getApplication();

    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      // no dumb mode for tests
      EmptyProgressIndicator i = new EmptyProgressIndicator();
      final int size = runner.queryNeededFiles(i);
      try {
        HeavyProcessLatch.INSTANCE.processStarted();
        if (size > 0) {
          runner.processFiles(i, false);
        }
        runner.updatingDone();
      }
      finally {
        HeavyProcessLatch.INSTANCE.processFinished();
      }
      return;
    }

    if (!forceDumbMode && !myDumb && application.isReadAccessAllowed()) {
      // if there are not so many files to process, process them on the spot without entering dumb mode
      final ProgressIndicator currentIndicator = ProgressManager.getInstance().getProgressIndicator();
      final ProgressIndicator indicator;
      if (currentIndicator != null) {
        indicator = currentIndicator;
        currentIndicator.pushState();
      }
      else {
        indicator = new EmptyProgressIndicator();
      }
      try {
        final int size = runner.queryNeededFiles(indicator);
        if (application.isHeadlessEnvironment() || (size + runner.getNumberOfPendingUpdateJobs(indicator)) < 50) {
          // If not that many files found, process them on the spot, avoiding entering dumb mode
          // Consider number of pending tasks as well, because they may take noticeable time to process even if the number of files is small
          try {
            HeavyProcessLatch.INSTANCE.processStarted();
            if (size > 0) {
              runner.processFiles(indicator, false);
            }
            runner.updatingDone();
          }
          finally {
            HeavyProcessLatch.INSTANCE.processFinished();
          }
          return;
        }
      }
      finally {
        if (currentIndicator != null) {
          currentIndicator.popState();
        }
      }
    }


    final IndexUpdateRunnable updateRunnable = new IndexUpdateRunnable(runner);

    UIUtil.invokeLaterIfNeeded(new DumbAwareRunnable() {
      public void run() {
        if (myProject.isDisposed()) {
          return;
        }
        // ok to test and set the flag like this, because the change is always done from dispatch thread
        final boolean wasDumb = myDumb;
        if (!wasDumb) {
          // always change dumb status inside write action.
          // This will ensure all active read actions are completed before the app goes dumb
          application.runWriteAction(new Runnable() {
            public void run() {
              myDumb = true;
              myPublisher.enteredDumbMode();

              updateRunnable.run();
            }
          });
        }
        else {
          myUpdatesQueue.addLast(updateRunnable);
        }
      }
    });
  }

  private void updateFinished() {
    myDumb = false;
    if (!myProject.isDisposed()) {
      myPublisher.exitDumbMode();
      FileEditorManagerEx.getInstanceEx(myProject).refreshIcons();
    }
    while (true) {
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
        LOG.error(e);
      }
    }
  }

  @Override
  public void showDumbModeNotification(final String message) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        final IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(myProject);
        StatusBarEx statusBar = (StatusBarEx)ideFrame.getStatusBar();
        statusBar.notifyProgressByBalloon(MessageType.WARNING, message, null, null);
      }
    });
  }

  private static final Ref<CacheUpdateRunner> NULL_ACTION = new Ref<CacheUpdateRunner>(null);

  public void waitForSmartMode() {
    final Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      assert !application.isDispatchThread();
      assert !application.isReadAccessAllowed();
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    runWhenSmart(new Runnable() {
      public void run() {
        semaphore.up();
      }
    });
    semaphore.waitFor();
  }

  public JComponent wrapGently(@NotNull JComponent dumbUnawareContent, @NotNull Disposable parentDisposable) {
    final DumbUnawareHider wrapper = new DumbUnawareHider(dumbUnawareContent);
    wrapper.setContentVisible(!isDumb());
    getProject().getMessageBus().connect(parentDisposable).subscribe(DUMB_MODE, new DumbModeListener() {

      public void enteredDumbMode() {
        wrapper.setContentVisible(false);
      }

      public void exitDumbMode() {
        wrapper.setContentVisible(true);
      }
    });

    return wrapper;
  }

  private class IndexUpdateRunnable implements Runnable {
    private final CacheUpdateRunner myAction;
    private double myProcessedItems;
    private volatile int myTotalItems;
    private double myCurrentBaseTotal;

    public IndexUpdateRunnable(CacheUpdateRunner action) {
      myAction = action;
      myTotalItems = 0;
      myCurrentBaseTotal = 0;
    }

    public void run() {
      if (myProject.isDisposed()) {
        return;
      }

      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, IdeBundle.message("progress.indexing"), false) {

        private final BlockingQueue<Ref<CacheUpdateRunner>> myActionQueue = new LinkedBlockingQueue<Ref<CacheUpdateRunner>>();

        // /*no override for interfaces in jdk 1.5 */ @Override
        public void run(@NotNull final ProgressIndicator indicator) {
          if (indicator instanceof ProgressIndicatorEx) {
            ((ProgressIndicatorEx)indicator).addStateDelegate(new ProgressIndicatorBase() {
              double lastFraction;

              @Override
              public void setFraction(final double fraction) {
                if (fraction - lastFraction < 0.01d) return;
                lastFraction = fraction;
                UIUtil.invokeLaterIfNeeded(new Runnable() {
                  public void run() {
                    AppIcon.getInstance().setProgress("indexUpdate", AppIconScheme.Progress.INDEXING, fraction, true);
                  }
                });
              }

              @Override
              public void finish(@NotNull TaskInfo task) {
                UIUtil.invokeLaterIfNeeded(new Runnable() {
                  public void run() {
                    AppIcon appIcon = AppIcon.getInstance();
                    if (appIcon.hideProgress("indexUpdate")) {
                      appIcon.requestAttention(false);
                      appIcon.setOkBadge(true);
                    }
                  }
                });
              }
            });
          }

          final ProgressIndicator proxy = new DelegatingProgressIndicator(indicator) {
            @Override
            public void setFraction(double fraction) {
              super.setFraction((myProcessedItems + fraction * myCurrentBaseTotal) / myTotalItems);
            }
          };

          final ShutDownTracker shutdownTracker = ShutDownTracker.getInstance();
          final Thread self = Thread.currentThread();
          try {
            HeavyProcessLatch.INSTANCE.processStarted();
            shutdownTracker.registerStopperThread(self);
            runAction(proxy, myAction);
          }
          finally {
            shutdownTracker.unregisterStopperThread(self);
            HeavyProcessLatch.INSTANCE.processFinished();
          }
        }

        private void runAction(ProgressIndicator indicator, CacheUpdateRunner updateRunner) {
          do {
            int count = 0;
            try {
              indicator.setIndeterminate(true);
              indicator.setText(IdeBundle.message("progress.indexing.scanning"));
              count = updateRunner.queryNeededFiles(indicator);

              myCurrentBaseTotal = count;
              myTotalItems += count;

              indicator.setIndeterminate(false);
              indicator.setText(IdeBundle.message("progress.indexing.updating"));
              if (count > 0) {
                updateRunner.processFiles(indicator, true);
              }
              updateRunner.updatingDone();
            }
            finally {
              myProcessedItems += count;
              UIUtil.invokeLaterIfNeeded(new DumbAwareRunnable() {
                public void run() {
                  IndexUpdateRunnable nextUpdateRunnable = null;
                  try {
                    nextUpdateRunnable = myUpdatesQueue.isEmpty()? null : myUpdatesQueue.pullFirst();
                    if (nextUpdateRunnable == null) {
                      // really terminate the task
                      myActionQueue.offer(NULL_ACTION);
                    }
                    else {
                      //run next dumb action
                      // run next action under already existing progress indicator
                      myActionQueue.offer(new Ref<CacheUpdateRunner>(nextUpdateRunnable.myAction));
                    }
                  }
                  catch (Throwable e) {
                    myActionQueue.offer(NULL_ACTION);
                    LOG.info(e);
                  }
                  finally {
                    if (nextUpdateRunnable == null) {
                      updateFinished();
                    }
                  }
                }
              });

              // try to obtain the next action or terminate if no actions left
              Ref<CacheUpdateRunner> ref = null;
              do {
                try {
                  ref = myActionQueue.poll(500, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException e) {
                  LOG.info(e);
                }
                updateRunner = ref != null? ref.get() : null;
                if (myProject.isDisposed()) {
                  // just terminate the progress task
                  break;
                }
              }
              while (ref == null);
            }
          }
          while (updateRunner != null);
        }

      });
    }
  }

}
