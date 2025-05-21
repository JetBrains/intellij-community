// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.ide.progress.CancellableTaskCancellation
import com.intellij.platform.ide.progress.TaskCancellation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProgressIndicatorModel(
  private val progressIndicator: ProgressIndicatorEx,
  override val title: String,
  override val cancellation: TaskCancellation,
  override val visibleInStatusBar: Boolean,
) : ProgressModel {
  constructor(progressIndicator: ProgressIndicatorEx, title: String, isCancellable: Boolean) : this(progressIndicator, title, if (isCancellable) TaskCancellation.cancellable() else TaskCancellation.nonCancellable(), true)

  constructor(title: String, taskCancellation: TaskCancellation, visibleInStatusBar: Boolean = true, onCancel: () -> Unit) : this(ProgressIndicatorBase().apply {
    addStateDelegate(object : AbstractProgressIndicatorExBase() {
      override fun cancel() {
        onCancel.invoke()
        super.cancel()
      }
    })
  }, title, taskCancellation, visibleInStatusBar)

  fun setFraction(fraction: Double) {
    progressIndicator.fraction = fraction
  }

  override fun getText(): @NlsContexts.ProgressText String? {
    return progressIndicator.text
  }

  fun setText(@NlsContexts.ProgressText text: String?) {
    progressIndicator.text = text ?: ""
  }

  override fun getDetails(): @NlsContexts.ProgressDetails String? {
    return progressIndicator.text2
  }

  fun setText2(@NlsContexts.ProgressDetails text2: String?) {
    progressIndicator.text2 = text2 ?: ""
  }

  override fun getFraction(): Double {
    return progressIndicator.fraction
  }

  override fun isIndeterminate(): Boolean {
    return progressIndicator.isIndeterminate
  }

  fun setIndeterminate(isIndeterminate: Boolean) {
    progressIndicator.isIndeterminate = isIndeterminate
  }

  override fun isStopping(taskInfo: TaskInfo): Boolean {
    return progressIndicator.wasStarted() && (progressIndicator.isCanceled() || !progressIndicator.isRunning()) && !progressIndicator.isFinished(taskInfo)
  }

  override fun isRunning(): Boolean {
    return progressIndicator.isRunning
  }

  override fun cancel() {
    progressIndicator.cancel()
  }

  fun onProgressChange(action: () -> Unit) {
    progressIndicator.addStateDelegate(object : AbstractProgressIndicatorExBase() {
      override fun onProgressChange() {
        super.onProgressChange()
        action.invoke()
      }
    })
  }

  override fun isCancellable(): Boolean = cancellation is TaskCancellation.Cancellable

  @NlsContexts.Tooltip
  override fun getCancelTooltipText(): String {
    if (!isCancellable()) return ""
    return (cancellation as? CancellableTaskCancellation)?.tooltipText ?: ""
  }

  override fun finish(taskInfo: TaskInfo) {
    progressIndicator.finish(taskInfo)
  }

  fun addStateDelegate(delegate: ProgressIndicatorEx) {
    progressIndicator.addStateDelegate(delegate)
  }

  override fun isFinished(taskInfo: TaskInfo): Boolean = progressIndicator.isFinished(taskInfo)

  override fun getProgressIndicator(): ProgressIndicatorEx {
    return progressIndicator
  }

  override fun addOnFinishAction(action: (TaskInfo) -> Unit) {
    val indicator = object : AbstractProgressIndicatorExBase() {
      override fun finish(task: TaskInfo) {
        super.finish(task)
        action(task)
      }
    }
    progressIndicator.addStateDelegate(indicator)
  }

  override fun addOnChangeAction(action: () -> Unit) {
    val indicator = object : AbstractProgressIndicatorExBase() {
      override fun onProgressChange() {
        action()
      }

      override fun cancel() {
        super.cancel()
        action()
      }

      override fun stop() {
        super.stop()
        action()
      }
    }
    progressIndicator.addStateDelegate(indicator)
  }
}