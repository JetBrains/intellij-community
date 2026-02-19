// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GradleServerProgressIndicator(
  private val taskId: ExternalSystemTaskId,
  private val taskListener: ExternalSystemTaskNotificationListener?,
  val progressIndicator: ProgressIndicator,
) : TargetProgressIndicator {

  override fun addText(text: String, outputType: Key<*>) {
    taskListener?.onTaskOutput(taskId, text, ProcessOutputType.fromKey(outputType))
  }

  override fun stop(): Unit = progressIndicator.stop()

  override fun isStopped(): Boolean = !progressIndicator.isRunning

  fun cancel(): Unit = progressIndicator.cancel()

  override fun isCanceled(): Boolean = progressIndicator.isCanceled

  fun checkCanceled(): Unit = progressIndicator.checkCanceled()
}
