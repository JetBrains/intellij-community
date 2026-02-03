// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.service
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.NlsContexts.ProgressText
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Future

@ApiStatus.Internal
interface UnindexedFilesScannerExecutor {
  val isRunning: StateFlow<Boolean>
  val hasQueuedTasks: Boolean
  val startedOrStoppedEvent: Flow<*>
  val modificationTracker: ModificationTracker

  @Deprecated("Use suspendScanningAndIndexingThenRun(String, suspend () -> Unit)")
  fun suspendScanningAndIndexingThenRun(activityName: @ProgressText String, runnable: Runnable)

  suspend fun suspendScanningAndIndexingThenExecute(activityName: @ProgressText String, activity: suspend CoroutineScope.() -> Unit)

  fun suspendQueue()
  fun resumeQueue()
  fun cancelAllTasksAndWait()
  fun getPauseReason(): StateFlow<PersistentList<String>>

  fun submitTask(task: FilesScanningTask): Future<*>

  companion object {
    @JvmStatic
    fun getInstance(project: Project): UnindexedFilesScannerExecutor = project.service<UnindexedFilesScannerExecutor>()

    fun <T: MergeableQueueTask<T>> unwrapTask(task: MergingTaskQueue.QueuedTask<T>): T {
      return task.task
    }
  }
}