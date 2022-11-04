// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.ide.IdeBundle;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.RelayUiToDelegateIndicator;
import com.intellij.openapi.project.DumbServiceMergingTaskQueue.QueuedDumbModeTask;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;

final class DumbServiceGuiTaskQueue {
  private static final Logger LOG = Logger.getInstance(DumbServiceGuiTaskQueue.class);

  private final Project myProject;
  private final DumbServiceMergingTaskQueue myTaskQueue;

  /**
   * Per-task progress indicators. Modified from EDT only.
   * The task is removed from this map after it's finished or when the project is disposed.
   */

  DumbServiceGuiTaskQueue(@NotNull Project project,
                          @NotNull DumbServiceMergingTaskQueue queue) {
    myProject = project;
    myTaskQueue = queue;
  }

  void processTasksWithProgress(@NotNull StructuredIdeActivity activity,
                                @NotNull ProgressSuspender suspender,
                                @NotNull ProgressIndicator visibleIndicator) {
    while (true) {
      if (myProject.isDisposed()) break;

      try (QueuedDumbModeTask pair = myTaskQueue.extractNextTask()) {
        if (pair == null) break;

        AbstractProgressIndicatorExBase taskIndicator = (AbstractProgressIndicatorExBase)pair.getIndicator();
        ProgressIndicatorEx relayToVisibleIndicator = new RelayUiToDelegateIndicator(visibleIndicator);
        suspender.attachToProgress(taskIndicator);
        taskIndicator.addStateDelegate(relayToVisibleIndicator);
        pair.registerStageStarted(activity);

        try {
          HeavyProcessLatch.INSTANCE
            .performOperation(HeavyProcessLatch.Type.Indexing, IdeBundle.message("progress.performing.indexing.tasks"), () -> runSingleTask(pair));
        }
        finally {
          taskIndicator.removeStateDelegate(relayToVisibleIndicator);
        }
      }
    }
  }

  public void runBackgroundProcess(ProgressIndicator visibleIndicator, DumbServiceHeavyActivities heavyActivities) {
    try {
      ((ProgressManagerImpl)ProgressManager.getInstance()).markProgressSafe((UserDataHolder)visibleIndicator);
    }
    catch (Throwable throwable) {
      // PCE is not expected
      LOG.error(throwable);
    }

    // Only one thread can execute this method at the same time at this point. // TODO

    try (ProgressSuspender suspender = ProgressSuspender.markSuspendable(visibleIndicator, IdeBundle.message("progress.text.indexing.paused"))) {
      heavyActivities.setCurrentSuspenderAndSuspendIfRequested(suspender);
      DumbModeProgressTitle.getInstance(myProject).attachDumbModeProgress(visibleIndicator);

      StructuredIdeActivity activity = IndexingStatisticsCollector.INDEXING_ACTIVITY.started(myProject);

      ShutDownTracker.getInstance().executeWithStopperThread(Thread.currentThread(), ()-> {
        try {
          DumbServiceAppIconProgress.registerForProgress(myProject, (ProgressIndicatorEx)visibleIndicator);
          processTasksWithProgress(activity, suspender, visibleIndicator);
        }
        catch (Throwable unexpected) {
          LOG.error(unexpected);
        }
        finally {
          DumbModeProgressTitle.getInstance(myProject).removeDumpModeProgress(visibleIndicator);

          IndexingStatisticsCollector.logProcessFinished(activity, suspender.isClosed()
                                                                   ? IndexingStatisticsCollector.IndexingFinishType.TERMINATED
                                                                   : IndexingStatisticsCollector.IndexingFinishType.FINISHED);
        }
      });
    }
    finally {
      // myCurrentSuspender should already be null at this point unless we got here by exception. In any case, the suspender might have
      // got suspended after the last dumb task finished (or even after the last check cancelled call). This case is handled by
      // the ProgressSuspender close() method called at the exit of this try-with-resources block which removes the hook if it has been
      // previously installed.
      heavyActivities.resetCurrentSuspender();
    }
  }

  private static void runSingleTask(@NotNull final QueuedDumbModeTask task) {
    if (ApplicationManager.getApplication().isInternal()) LOG.info("Running dumb mode task: " + task.getInfoString());

    // nested runProcess is needed for taskIndicator to be honored in ProgressManager.checkCanceled calls deep inside tasks
    ProgressManager.getInstance().runProcess(() -> {
      try {
        task.executeTask();
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (Throwable unexpected) {
        LOG.error("Failed to execute task " + task + ". " + unexpected.getMessage(), unexpected);
      }
    }, task.getIndicator());
  }
}
