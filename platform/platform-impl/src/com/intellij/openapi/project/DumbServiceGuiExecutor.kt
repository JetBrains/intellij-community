// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.project.DumbModeStatisticsCollector.IndexingFinishType
import com.intellij.openapi.project.DumbModeStatisticsCollector.logProcessFinished
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.util.indexing.IndexingBundle
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.CoroutineContext

@Internal
class DumbServiceGuiExecutor(project: Project, queue: DumbServiceMergingTaskQueue, listener: ExecutorStateListener)
  : MergingQueueGuiExecutor<DumbModeTask>(project, queue, listener,
                                          IndexingBundle.message("progress.indexing"),
                                          IndexingBundle.message("progress.indexing.paused")) {

  override val taskId = IndexingType.INDEXING

  internal fun guiSuspender(): MergingQueueGuiSuspender = super.guiSuspender

  override fun processTasksWithProgress(suspender: ProgressSuspender?,
                                        visibleIndicator: ProgressIndicator,
                                        parentActivity: StructuredIdeActivity?): SubmissionReceipt? {
    if (parentActivity != null) {
      thisLogger().error("DumbServiceGuiExecutor is supposed to have a plain structure of activities. $parentActivity")
    }
    val childActivity = DumbModeStatisticsCollector.DUMB_MODE_ACTIVITY.started(project)
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

  @TestOnly
  internal suspend fun waitUntilFinished() {
    isRunning.first { !it }
  }

  @ApiStatus.Internal
  enum class IndexingType : CoroutineContext.Element, IntelliJContextElement {
    SCANNING, INDEXING;

    override val key: CoroutineContext.Key<IndexingType> get() = IndexingType


    override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

    companion object Key : CoroutineContext.Key<IndexingType>
  }
}
