// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.ProgressIndicatorModel
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProgressModel
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.ide.progress.BackgroundTaskOwnerKind
import com.intellij.platform.ide.progress.TaskHandle
import com.intellij.platform.ide.progress.TaskStatus
import com.intellij.platform.ide.progress.TaskStorage
import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

private val LOG = logger<TaskInfoEntityCollector>()

internal class TaskInfoEntityCollector(cs: CoroutineScope) {
  init {
    LOG.trace { "TaskInfoEntityCollector started for application" }
    collectActiveTasks(cs, project = null)
  }
}

@Service(Service.Level.PROJECT)
internal class PerProjectTaskInfoEntityCollector(private val project: Project, private val cs: CoroutineScope) {
  fun startCollectingActiveTasks() {
    LOG.trace { "PerProjectTaskInfoEntityCollector started for $project" }
    collectActiveTasks(cs, project)
  }

  /**
   * Shows the tasks of this project only, see [isOwnTask]. For a project that shows in the frame of another project: the
   * collector of that project shows the tasks of all frames already, so this one adds the tasks of its own project and nothing else.
   */
  fun startCollectingOwnTasks() {
    LOG.trace { "PerProjectTaskInfoEntityCollector started for the own tasks of $project" }
    val projectId = project.projectId()
    TaskStorage.getInstance().collectEachTask(cs) { task ->
      if (isOwnTask(task.info.ownerKind, task.info.projectId, projectId)) {
        showTaskIndicator(project, task)
      }
    }
  }
}

private fun collectActiveTasks(cs: CoroutineScope, project: Project?) {
  TaskStorage.getInstance().collectEachTask(cs) { task ->
    if (isTaskShownByCollector(task.info.ownerKind, task.info.projectId, project?.projectId())) {
      showTaskIndicator(project, task)
    }
  }
}

/** Tells whether a task belongs to the project of a collector that shows its own tasks only, see [PerProjectTaskInfoEntityCollector.startCollectingOwnTasks]. */
internal fun isOwnTask(ownerKind: BackgroundTaskOwnerKind, taskProjectId: ProjectId?, collectorProjectId: ProjectId): Boolean {
  return ownerKind == BackgroundTaskOwnerKind.PROJECT && taskProjectId == collectorProjectId
}

/**
 * Decides if a collector shows a task in its frame.
 *
 * The application collector shows the tasks of the default project and the tasks of the last focused frame.
 * A per-project collector shows the tasks of its project and the tasks of all frames.
 *
 * @param collectorProjectId the project id of a per-project collector, or null for the application collector
 */
internal fun isTaskShownByCollector(
  ownerKind: BackgroundTaskOwnerKind,
  taskProjectId: ProjectId?,
  collectorProjectId: ProjectId?,
): Boolean {
  return when (ownerKind) {
    BackgroundTaskOwnerKind.PROJECT -> taskProjectId == collectorProjectId
    // we make only the application collector to handle the UI of the last-focused-only progress
    // otherwise we would have multiple projects attempting to draw UI for the same last-focused frame
    BackgroundTaskOwnerKind.GUESS -> collectorProjectId == null
    // we ignore the application collector by filtering for non-null project id
    BackgroundTaskOwnerKind.ALL_FRAMES -> collectorProjectId != null
  }
}

private suspend fun CoroutineScope.showTaskIndicator(project: Project?, task: TaskHandle) {
  LOG.trace { "Showing indicator for $task, project=$project" }

  val progressModel = ProgressIndicatorModel(task.info.title, task.info.cancellation, visibleInStatusBar = task.info.visibleInStatusBar) {
    LOG.trace { "Cancelling $task" }
    TaskStorage.getInstance().setStatus(task, TaskStatus.Canceled(TaskStatus.Source.USER))
  }

  // a null project makes the indicator use the last focused frame
  val indicatorProject = when {
    project != null -> project
    task.info.ownerKind == BackgroundTaskOwnerKind.GUESS -> null
    else -> serviceAsync<ProjectManager>().defaultProject
  }
  showIndicator(
    indicatorProject,
    progressModel,
    task.progress.filterNotNull()
  )

  collectSuspendableChanges(task, progressModel)
}

private suspend fun collectSuspendableChanges(task: TaskHandle, progressModel: ProgressModel) {
  task.suspension.collectLatest { suspension ->
    coroutineScope {
      markSuspendable(task, suspension, progressModel)
    }
  }
}

private suspend fun CoroutineScope.markSuspendable(task: TaskHandle, suspension: TaskSuspension, progressModel: ProgressModel) {
  if (suspension !is TaskSuspension.Suspendable) return

  // HACK: tempIndicator is required to avoid runProcess stopping the original indicator when the execution is finished
  val tempIndicator = ProgressIndicatorBase()
  val suspender = ProgressManager.getInstance().runProcess<ProgressSuspender>(
    { ProgressSuspender.markSuspendable(tempIndicator, suspension.suspendText) }, tempIndicator)

  try {
    val suspenderStateChange = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    ProgressSuspenderTracker.getInstance().startTracking(suspender, object : ProgressSuspenderTracker.SuspenderListener {
      override fun onStateChanged(progressSuspender: ProgressSuspender) {
        suspenderStateChange.tryEmit(Unit)
      }
    })

    // Instead of markSuspendable, which has to be called under runProcess, we can use attachToProgress on the already created suspender
    suspender.attachToProgress(progressModel.getProgressIndicator()) //propagate events to original indicator

    val storage = TaskStorage.getInstance()
    launch {
      suspenderStateChange.collectLatest {
        if (suspender.isSuspended) {
          storage.setStatus(task, TaskStatus.Paused(suspender.suspendedText, TaskStatus.Source.USER))
        }
        else {
          storage.setStatus(task, TaskStatus.Running(TaskStatus.Source.USER))
        }
      }
    }

    launch {
      // We shouldn't process events generated by TaskInfoEntityCollector to avoid infinite update cycles
      task.status
        .filter { it.source != TaskStatus.Source.USER }
        .collect { status ->
          when (status) {
            is TaskStatus.Paused -> suspender.suspendProcess(status.reason)
            is TaskStatus.Running -> suspender.resumeProcess()
            is TaskStatus.Canceled -> { /* do nothing */ }
          }
        }
    }

    awaitCancellation()
  }
  finally {
    ProgressSuspenderTracker.getInstance().stopTracking(suspender)
    suspender.close()
  }
}