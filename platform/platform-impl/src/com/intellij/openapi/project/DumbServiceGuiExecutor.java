// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.ide.IdeBundle;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.project.DumbServiceMergingTaskQueue.QueuedDumbModeTask;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;

final class DumbServiceGuiExecutor extends MergingQueueGuiExecutor<DumbModeTask> {
  private final DumbServiceHeavyActivities myHeavyActivities;
  private StructuredIdeActivity activity;

  DumbServiceGuiExecutor(@NotNull Project project,
                         @NotNull DumbServiceMergingTaskQueue queue,
                         @NotNull DumbServiceHeavyActivities heavyActivities,
                         @NotNull DumbTaskListener listener) {
    super(project, queue, listener);
    myHeavyActivities = heavyActivities;
  }

  @Override
  protected void processTasksWithProgress(@NotNull ProgressSuspender suspender,
                                          @NotNull ProgressIndicator visibleIndicator) {
    Project project = getProject();
    activity = DumbModeStatisticsCollector.DUMB_MODE_ACTIVITY.started(project);
    try {
      DumbServiceAppIconProgress.registerForProgress(project, (ProgressIndicatorEx)visibleIndicator);
      DumbModeProgressTitle.getInstance(project).attachDumbModeProgress(visibleIndicator);
      myHeavyActivities.setCurrentSuspenderAndSuspendIfRequested(suspender);

      super.processTasksWithProgress(suspender, visibleIndicator);
    }
    finally {
      // myCurrentSuspender should already be null at this point unless we got here by exception. In any case, the suspender might have
      // got suspended after the last dumb task finished (or even after the last check cancelled call). This case is handled by
      // the ProgressSuspender close() method called at the exit of this try-with-resources block which removes the hook if it has been
      // previously installed.
      myHeavyActivities.resetCurrentSuspender();
      DumbModeStatisticsCollector.logProcessFinished(activity, suspender.isClosed()
                                                               ? DumbModeStatisticsCollector.IndexingFinishType.TERMINATED
                                                               : DumbModeStatisticsCollector.IndexingFinishType.FINISHED);
      activity = null;
      DumbModeProgressTitle.getInstance(project).removeDumpModeProgress(visibleIndicator);
    }
  }

  @Override
  protected void runSingleTask(@NotNull MergingTaskQueue.QueuedTask<DumbModeTask> task) {
    ((QueuedDumbModeTask)task).registerStageStarted(activity);
    HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Indexing,
                                                IdeBundle.message("progress.performing.indexing.tasks"),
                                                () -> super.runSingleTask(task));
  }
}
