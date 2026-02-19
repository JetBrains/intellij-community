// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.project.DumbModeStatisticsCollector.IndexingFinishType
import com.intellij.openapi.project.DumbModeStatisticsCollector.logProcessFinished
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.progress.RawProgressReporter
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

  override fun processTasksWithProgress(reporter: RawProgressReporter,
                                        parentActivity: StructuredIdeActivity?): SubmissionReceipt? {
    if (parentActivity != null) {
      thisLogger().error("DumbServiceGuiExecutor is supposed to have a plain structure of activities. $parentActivity")
    }
    val childActivity = DumbModeStatisticsCollector.DUMB_MODE_ACTIVITY.started(project)
    var taskCompletedNormally = false
    return try {
      val dumbServiceAppIconProgress = DumbServiceAppIconProgress(project)
      val delegatingReporter = delegatingProgressReporter(dumbServiceAppIconProgress, reporter)
      try {
        super.processTasksWithProgress(delegatingReporter, childActivity).also {
          taskCompletedNormally = true
        }
      } finally {
          dumbServiceAppIconProgress.finish(DummyTaskInfo) // none of TaskInfo methods are used inside, this is just to satisfy the API
      }
    }
    finally {
      logProcessFinished(childActivity, if (taskCompletedNormally) IndexingFinishType.FINISHED else IndexingFinishType.TERMINATED)
    }
  }

  private object DummyTaskInfo: TaskInfo {
    override fun getTitle(): @NlsContexts.ProgressTitle String = ""
    override fun getCancelText(): @NlsContexts.Button String? = null

    override fun getCancelTooltipText(): @NlsContexts.Tooltip String? = null

    override fun isCancellable(): Boolean = false
  }

  private fun delegatingProgressReporter(dumbServiceProgress: DumbServiceAppIconProgress, reporter: RawProgressReporter): RawProgressReporter = object: RawProgressReporter by reporter {
    override fun fraction(fraction: Double?) {
      if (fraction != null) {
        dumbServiceProgress.fraction = fraction
      }
      reporter.fraction(fraction)
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
