// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.indexing.IndexingBundle
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class UnindexedFilesScannerExecutor(project: Project)
  : Disposable,
    MergingQueueGuiExecutor<FilesScanningTask>(project, MergingTaskQueue(), TaskQueueListener(),
                                               IndexingBundle.message("progress.indexing.scanning"),
                                               IndexingBundle.message("progress.indexing.scanning.paused")) {
  private val runningDumbTask = AtomicReference<ProgressIndicator>()

  // note that shouldShowProgressIndicator = false in UnindexedFilesScannerExecutor, so there is no suspender for the progress indicator
  private val pauseReason = MutableStateFlow<PersistentList<String>>(persistentListOf())
  fun getPauseReason(): StateFlow<PersistentList<String>> = pauseReason

  private class TaskQueueListener : ExecutorStateListener {
    override fun beforeFirstTask(): Boolean = true
    override fun afterLastTask(latestReceipt: SubmissionReceipt?) = Unit
  }

  fun submitTask(task: FilesScanningTask) {
    // Two tasks with limited checks should be just run one after another.
    // A case of a full check followed by a limited change cancelling first one and making a full check anew results
    // in endless restart of full checks on Windows with empty Maven cache.
    // So only in case the second one is a full check should the first one be cancelled.
    if (task.isFullIndexUpdate()) {
      // we don't want to execute any of the existing tasks - the only task we want to execute will be submitted few lines below
      cancelAllTasks()
      cancelRunningScannerTaskInDumbQueue()
    }
    if (shouldScanInSmartMode()) {
      startTaskInSmartMode(task)
    }
    else {
      startTaskInDumbMode(task)
    }

    if (shouldScanInSmartMode() && isSynchronousTaskExecution) {
      SyncTaskWaiter(project, IndexingBundle.message("progress.indexing.scanning"), isRunning).waitUntilFinished()
    }
  }

  private fun startTaskInSmartMode(task: FilesScanningTask) {
    taskQueue.addTask(task)
    startBackgroundProcess(onFinish = {})
  }

  private fun startTaskInDumbMode(task: FilesScanningTask) {
    wrapAsDumbTask(task).queue(project)
  }

  @VisibleForTesting
  fun wrapAsDumbTask(task: FilesScanningTask): DumbModeTask {
    return FilesScanningTaskAsDumbModeTaskWrapper(project, task, runningDumbTask)
  }

  private fun cancelRunningScannerTaskInDumbQueue() {
    val indicator = runningDumbTask.get()
    if (indicator != null) {
      indicator.cancel()
      val suspender = ProgressSuspender.getSuspender(indicator)
      if (suspender != null && suspender.isSuspended) {
        suspender.resumeProcess()
      }
    }
  }

  fun cancelAllTasksAndWait() {
    cancelAllTasks() // this also cancels running task even if they paused by ProgressSuspender
    while (isRunning.value && !project.isDisposed) {
      PingProgress.interactWithEdtProgress()
      LockSupport.parkNanos(50_000_000)
    }
  }

  override fun dispose() {
    taskQueue.disposePendingTasks()
  }

  override fun shouldShowProgressIndicator(): Boolean = false // will be reported asynchronously via IndexingProgressUIReporter

  /**
   * This method does not have "happens before" semantics. It requests GUI suspender to suspend and executes runnable without waiting for
   * all the running tasks to pause.
   */
  fun suspendScanningAndIndexingThenRun(activityName: @ProgressText String, runnable: Runnable) {
    suspendAndRun(activityName) { // we only need this call to suspend legacy dumb scanning mode
      pauseReason.update { it.add(activityName) }
      try {
        DumbService.getInstance(project).suspendIndexingAndRun(activityName, runnable)
      }
      finally {
        pauseReason.update { it.remove(activityName) }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): UnindexedFilesScannerExecutor = project.service<UnindexedFilesScannerExecutor>()

    @JvmStatic
    fun shouldScanInSmartMode(): Boolean = !DumbServiceImpl.isSynchronousTaskExecution && Registry.`is`("scanning.in.smart.mode", true)

    val isSynchronousTaskExecution: Boolean = DumbServiceImpl.isSynchronousTaskExecution
  }
}
