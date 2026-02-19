// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.ide.progress.TaskCancellation

interface ProgressModel {
  val title: String

  val cancellation: TaskCancellation

  val visibleInStatusBar: Boolean

  fun getText(): @NlsContexts.ProgressText String?

  fun getDetails(): @NlsContexts.ProgressDetails String?

  fun getFraction(): Double

  fun isIndeterminate(): Boolean

  fun isStopping(taskInfo: TaskInfo): Boolean

  fun isRunning(): Boolean

  fun cancel()

  fun isCancellable(): Boolean

  @NlsContexts.Tooltip
  fun getCancelTooltipText(): String

  // removes indicator from UI if added
  fun finish(taskInfo: TaskInfo)

  fun isFinished(taskInfo: TaskInfo): Boolean

  fun addOnFinishAction(action: (TaskInfo) -> Unit)

  fun addOnChangeAction(action: () -> Unit)

  //TODO remove, now it's necessary for ProgressSuspender
  fun getProgressIndicator(): ProgressIndicatorEx
}