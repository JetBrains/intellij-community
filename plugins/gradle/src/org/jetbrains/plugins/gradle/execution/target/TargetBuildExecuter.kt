// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation
import org.gradle.tooling.internal.consumer.BlockingResultHandler
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters

abstract class TargetBuildExecuter<T : AbstractLongRunningOperation<T>, R : Any?>(
  private val environmentConfiguration: TargetEnvironmentConfiguration,
  private val taskId: ExternalSystemTaskId?,
  private val taskListener: ExternalSystemTaskNotificationListener?,
  parameters: ConnectionParameters) : AbstractLongRunningOperation<T>(parameters) {

  abstract val targetBuildParametersBuilder: TargetBuildParameters.Builder

  protected fun runAndGetResult(): R = BlockingResultHandler(Any::class.java).run {
    runWithHandler(this)
    @Suppress("UNCHECKED_CAST")
    result as R
  }

  protected fun runWithHandler(handler: ResultHandler<Any?>) {
    val project: Project = taskId?.findProject() ?: return
    val targetProgressIndicator: TargetEnvironmentAwareRunProfileState.TargetProgressIndicator = object : TargetEnvironmentAwareRunProfileState.TargetProgressIndicator {
      @Volatile
      var stopped = false
      override fun addText(text: String, outputType: Key<*>) {
        taskListener?.onTaskOutput(taskId, text, outputType === ProcessOutputTypes.STDOUT)
      }

      override fun isCanceled(): Boolean = false
      override fun stop() {
        stopped = true
      }

      override fun isStopped(): Boolean = stopped
    }
    val gradleProxyRunner = GradleServerRunner(project, consumerOperationParameters)
    gradleProxyRunner.run(environmentConfiguration,
                          targetBuildParametersBuilder,
                          targetProgressIndicator,
                          handler)
  }
}