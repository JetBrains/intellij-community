// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.ide.progress.*
import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.projectId
import fleet.kernel.rete.asValuesFlow
import fleet.kernel.rete.collect
import fleet.kernel.rete.filter
import fleet.kernel.tryWithEntities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val LOG = logger<TaskInfoEntityCollector>()

private class TaskInfoEntityCollector(cs: CoroutineScope) {
  init {
    LOG.trace { "TaskInfoEntityCollector started for application"}
    collectActiveTasks(cs, project = null)
  }
}

private class PerProjectTaskInfoEntityCollector(project: Project, cs: CoroutineScope) {
  init {
    LOG.trace { "PerProjectTaskInfoEntityCollector started for $project"}
    collectActiveTasks(cs, project)
  }
}

private fun collectActiveTasks(cs: CoroutineScope, project: Project?) {
  cs.launch {
    val projectOrDefault = project ?: serviceAsync<ProjectManager>().defaultProject
    withKernel {
      activeTasks
        .filter { it.projectEntity?.projectId == project?.projectId() }
        .collect { task ->
          if (!isRhizomeProgressEnabled) return@collect

          showTaskIndicator(cs, projectOrDefault, task)
        }
    }
  }
}

private fun showTaskIndicator(cs: CoroutineScope, project: Project, task: TaskInfoEntity) {
  cs.launch {
    withKernel {
      tryWithEntities(task) {
        LOG.trace { "Showing indicator for task: entityId=${task.eid}, title=${task.title}, project=$project" }
        val indicator = taskCancellingIndicator(this, task)
        showIndicator(
          project,
          indicator,
          taskInfo(task.title, task.cancellation),
          task.updates.asValuesFlow()
        )

        markSuspendable(task, indicator)
      }
    }
  }
}

private suspend fun CoroutineScope.markSuspendable(task: TaskInfoEntity, indicator: ProgressIndicator) {
  val suspendableInfo = task.suspendable
  if (suspendableInfo !is TaskSuspension.Suspendable) return

  val suspender = ProgressManager.getInstance().runProcess<ProgressSuspender>(
    { ProgressSuspender.markSuspendable(indicator, suspendableInfo.suspendText) }, indicator)

  val suspenderStateChange = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  ProgressSuspenderTracker.getInstance().startTracking(suspender, object : ProgressSuspenderTracker.SuspenderListener {
    override fun onStateChanged(progressSuspender: ProgressSuspender) {
      suspenderStateChange.tryEmit(Unit)
    }
  })

  try {
    launch {
      suspenderStateChange.collectLatest {
        if (suspender.isSuspended) {
          TaskManager.pauseTask(task, suspender.suspendedText, TaskStatus.Source.USER)
        }
        else {
          TaskManager.resumeTask(task, TaskStatus.Source.USER)
        }
      }
    }

    launch {
      task.statuses
        .filter { it.source == TaskStatus.Source.SYSTEM }
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
  }
}

private fun taskCancellingIndicator(cs: CoroutineScope, taskInfo: TaskInfoEntity): ProgressIndicatorEx {
  val title = taskInfo.title
  val entityId = taskInfo.eid
  val indicator = ProgressIndicatorBase()
  indicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
    override fun cancel() {
      LOG.trace { "Cancelling task: entityId=$entityId, title=$title"}
      cs.launch {
        TaskManager.cancelTask(taskInfo, TaskStatus.Source.USER)
      }
      super.cancel()
    }
  })
  return indicator
}