// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsContexts.ProgressText
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface UnindexedFilesScannerExecutor {
  val isRunning: StateFlow<Boolean>
  val hasQueuedTasks: Boolean
  val startedOrStoppedEvent: Flow<*>

  fun suspendScanningAndIndexingThenRun(activityName: @ProgressText String, runnable: Runnable)
  fun suspendQueue()
  fun resumeQueue(onFinish: () -> Unit)
  fun cancelAllTasksAndWait()
  fun getPauseReason(): StateFlow<PersistentList<String>>

  fun submitTask(task: FilesScanningTask)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): UnindexedFilesScannerExecutor = project.service<UnindexedFilesScannerExecutor>()

    @JvmStatic
    fun shouldScanInSmartMode(): Boolean = true

    fun <T: MergeableQueueTask<T>> unwrapTask(task: MergingTaskQueue.QueuedTask<T>): T {
      return task.task
    }
  }
}