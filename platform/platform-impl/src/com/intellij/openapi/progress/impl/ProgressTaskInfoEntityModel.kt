// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.ProgressModel
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.ide.progress.*
import fleet.kernel.rete.collect
import fleet.kernel.tryWithEntities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<ProgressTaskInfoEntityModel>()

@ApiStatus.Internal
class ProgressTaskInfoEntityModel(val taskInfoEntity: TaskInfoEntity, val cs: CoroutineScope) : ProgressModel {
  private val onFinishFlow = MutableSharedFlow<TaskInfo>()
  private val onChangeFlow = MutableSharedFlow<Unit>()
  private val eid = taskInfoEntity.eid
  private var progressStatus: TaskStatus? = null

  //TODO remove
  // Will be removed in the next iteration when the new implementation without using ProgressSuspender will be introduced for ProgressTaskInfoEntityModel
  private val indicator: ProgressIndicatorEx = AbstractProgressIndicatorExBase()
  override val title: String = taskInfoEntity.title
  override val cancellation: TaskCancellation = taskInfoEntity.cancellation
  override fun getText(): @NlsContexts.ProgressText String? {
    return progressText
  }

  override val visibleInStatusBar: Boolean
    get() = taskInfoEntity.visibleInStatusBar

  init {
    cs.launch {
      tryWithEntities(taskInfoEntity) {
        taskInfoEntity.statuses.collect { status: TaskStatus ->
          progressStatus = status
          emitOnChange()
        }
      }
      val taskInfo = taskInfo(title, cancellation)
      LOG.trace { "Hiding indicator for task: ${title}" }
      finish(taskInfo) // removes indicator from UI if added

    }
    cs.launch {
      tryWithEntities(taskInfoEntity) {
        taskInfoEntity.updates.collect { state ->
          progressText = state.text
          progressDetails = state.details
          state.fraction?.let {
            progressFraction = it
          }
          emitOnChange()
        }
      }
    }
  }

  override fun getDetails(): @NlsContexts.ProgressDetails String? {
    return progressDetails
  }

  override fun getFraction(): Double {
    return progressFraction
  }

  override fun isIndeterminate(): Boolean {
    return progressFraction <= 0.0
  }

  private var progressText: @NlsContexts.ProgressText String? = null

  private var progressDetails: @NlsContexts.ProgressDetails String? = null

  private var progressFraction: Double = 0.0

  private var stopping: Boolean = false

  override fun isStopping(taskInfo: TaskInfo): Boolean {
    return progressStatus is TaskStatus.Canceled
  }

  override fun isRunning(): Boolean {
    return progressStatus is TaskStatus.Running
  }

  override fun cancel() {
    stopping = true
    LOG.trace { "Cancelling task: entityId=${eid}, title=$title" }
    cs.launch {
      TaskManager.cancelTask(taskInfoEntity, TaskStatus.Source.USER)
    }
  }

  override fun isCancellable(): Boolean {
    return cancellation is TaskCancellation.Cancellable
  }

  override fun getCancelTooltipText(): String {
    return (cancellation as? CancellableTaskCancellation)?.tooltipText ?: ""
  }

  private var isFinished = false

  override fun finish(taskInfo: TaskInfo) {
    isFinished = true
    indicator.finish(taskInfo)
    onFinish(taskInfo)
  }

  override fun isFinished(taskInfo: TaskInfo): Boolean {
    return isFinished
  }

  override fun addOnFinishAction(action: (TaskInfo) -> Unit) {
    cs.launch {
      onFinishFlow.collect { action(it) }
    }
  }

  override fun addOnChangeAction(action: () -> Unit) {
    cs.launch {
      onChangeFlow.collect { action.invoke() }
    }
  }

  private fun onFinish(taskInfo: TaskInfo) {
    cs.launch {
      onFinishFlow.emit(taskInfo)
    }
  }

  private fun emitOnChange() {
    cs.launch {
      onChangeFlow.emit(Unit)
    }
  }

  //TODO remove, now it's necessary for ProgressSuspender
  override fun getProgressIndicator(): ProgressIndicatorEx {
    return indicator
  }
}
