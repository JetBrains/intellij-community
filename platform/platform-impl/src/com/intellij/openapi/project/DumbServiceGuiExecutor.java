// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.ide.IdeBundle;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DumbServiceGuiExecutor extends MergingQueueGuiExecutor<DumbModeTask> {

  DumbServiceGuiExecutor(@NotNull Project project,
                         @NotNull DumbServiceMergingTaskQueue queue,
                         @NotNull MergingQueueGuiExecutor.ExecutorStateListener listener) {
    super(project, queue, listener, IndexingBundle.message("progress.indexing"), IndexingBundle.message("progress.indexing.paused"));
  }

  @Override
  protected SubmissionReceipt processTasksWithProgress(@NotNull ProgressSuspender suspender,
                                                       @NotNull ProgressIndicator visibleIndicator,
                                                       @Nullable StructuredIdeActivity parentActivity) {
    Project project = getProject();
    StructuredIdeActivity childActivity = createChildActivity(parentActivity);

    try {
      DumbServiceAppIconProgress.registerForProgress(project, (ProgressIndicatorEx)visibleIndicator);
      DumbModeProgressTitle.getInstance(project).attachDumbModeProgress(visibleIndicator);

      return super.processTasksWithProgress(suspender, visibleIndicator, childActivity);
    }
    finally {
      DumbModeStatisticsCollector.logProcessFinished(childActivity, suspender.isClosed()
                                                                    ? DumbModeStatisticsCollector.IndexingFinishType.TERMINATED
                                                                    : DumbModeStatisticsCollector.IndexingFinishType.FINISHED);
      DumbModeProgressTitle.getInstance(project).removeDumbModeProgress(visibleIndicator);
    }
  }

  @NotNull
  private StructuredIdeActivity createChildActivity(@Nullable StructuredIdeActivity parentActivity) {
    Project project = getProject();
    if (parentActivity == null) {
      return DumbModeStatisticsCollector.DUMB_MODE_ACTIVITY.started(project);
    }
    else {
      return DumbModeStatisticsCollector.DUMB_MODE_ACTIVITY.startedWithParent(project, parentActivity);
    }
  }

  @Override
  protected void runSingleTask(@NotNull MergingTaskQueue.QueuedTask<DumbModeTask> task, @Nullable StructuredIdeActivity activity) {
    HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Indexing,
                                                IdeBundle.message("progress.performing.indexing.tasks"),
                                                () -> super.runSingleTask(task, activity));
  }
}
