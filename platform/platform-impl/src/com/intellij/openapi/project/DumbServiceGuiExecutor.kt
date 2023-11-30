// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.ide.IdeBundle
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.project.DumbModeStatisticsCollector.IndexingFinishType
import com.intellij.openapi.project.DumbModeStatisticsCollector.logProcessFinished
import com.intellij.openapi.project.MergingTaskQueue.QueuedTask
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.io.storage.HeavyProcessLatch
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
class DumbServiceGuiExecutor(project: Project, queue: DumbServiceMergingTaskQueue, listener: ExecutorStateListener)
  : MergingQueueGuiExecutor<DumbModeTask>(project, queue, listener,
                                          IndexingBundle.message("progress.indexing"),
                                          IndexingBundle.message("progress.indexing.paused")) {

  internal fun guiSuspender(): MergingQueueGuiSuspender = super.guiSuspender

  override fun processTasksWithProgress(suspender: ProgressSuspender?,
                                        visibleIndicator: ProgressIndicator,
                                        parentActivity: StructuredIdeActivity?): SubmissionReceipt? {
    val childActivity = createChildActivity(parentActivity)
    var taskCompletedNormally = false
    return try {
      if (visibleIndicator is ProgressIndicatorEx) DumbServiceAppIconProgress.registerForProgress(project, visibleIndicator)
      DumbModeProgressTitle.getInstance(project).attachDumbModeProgress(visibleIndicator)
      super.processTasksWithProgress(suspender, visibleIndicator, childActivity).also {
        taskCompletedNormally = true
      }
    }
    finally {
      logProcessFinished(childActivity, if (taskCompletedNormally) IndexingFinishType.FINISHED else IndexingFinishType.TERMINATED)
      DumbModeProgressTitle.getInstance(project).removeDumbModeProgress(visibleIndicator)
    }
  }

  private fun createChildActivity(parentActivity: StructuredIdeActivity?): StructuredIdeActivity {
    return if (parentActivity == null) {
      DumbModeStatisticsCollector.DUMB_MODE_ACTIVITY.started(project)
    }
    else {
      DumbModeStatisticsCollector.DUMB_MODE_ACTIVITY.startedWithParent(project, parentActivity)
    }
  }

  override fun runSingleTask(task: QueuedTask<DumbModeTask>, activity: StructuredIdeActivity?) {
    HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Indexing, IdeBundle.message("progress.performing.indexing.tasks")) {
      super.runSingleTask(task, activity)
    }
  }
}
