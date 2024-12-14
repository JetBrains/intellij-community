// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GradleServerProgressIndicator(
  private val taskId: ExternalSystemTaskId,
  private val taskListener: ExternalSystemTaskNotificationListener?,
) : TargetProgressIndicator {
  val progressIndicator = EmptyProgressIndicator().apply { start() }

  override fun addText(text: String, outputType: Key<*>) {
    taskListener?.onTaskOutput(taskId, text, outputType != ProcessOutputTypes.STDERR)
  }

  override fun stop() = progressIndicator.stop()
  override fun isStopped(): Boolean = !progressIndicator.isRunning
  fun cancel() = progressIndicator.cancel()
  override fun isCanceled(): Boolean = progressIndicator.isCanceled
  fun checkCanceled() = progressIndicator.checkCanceled()
}
