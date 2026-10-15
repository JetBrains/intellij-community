// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.NlsContexts.ProgressText
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Future
import java.util.function.Predicate

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

  /**
   * Returns whether a queued scanning task waits until the project leaves dumb mode.
   */
  fun scanningWaitsForNonDumbMode(): Boolean = false

  /**
   * Overrides the `scanning.waits.for.non.dumb.mode` registry value. `null` restores the registry value.
   */
  @TestOnly
  fun overrideScanningWaitsForNonDumbMode(newValue: Boolean?) {
  }

  /**
   * Lets the executor run only the scanning tasks whose indexing reason matches [filter], until [disposable] is disposed.
   */
  @TestOnly
  fun setIndexingReasonFilterInTest(disposable: Disposable, filter: Predicate<String?>) {
  }

  /**
   * Queues a scanning task for the whole project with [indexingReason].
   */
  fun queueFullScanning(indexingReason: String): Future<*> {
    throw UnsupportedOperationException()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): UnindexedFilesScannerExecutor = project.service<UnindexedFilesScannerExecutor>()

    @JvmStatic
    fun isScanningInProgress(project: Project): Boolean {
      val executor = getInstance(project)
      return executor.hasQueuedTasks || executor.isRunning.value
    }

    fun <T: MergeableQueueTask<T>> unwrapTask(task: MergingTaskQueue.QueuedTask<T>): T {
      return task.task
    }
  }
}