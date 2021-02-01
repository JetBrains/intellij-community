// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.util.PathMapper
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.PhasedBuildAction
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters.PhasedBuildActionParametersBuilder

class TargetPhasedBuildActionExecuter(environmentConfiguration: TargetEnvironmentConfiguration,
                                      targetPathMapper: PathMapper?,
                                      taskId: ExternalSystemTaskId?,
                                      taskListener: ExternalSystemTaskNotificationListener?,
                                      parameters: ConnectionParameters,
                                      private val projectsLoadedAction: PhasedBuildAction.BuildActionWrapper<Any>?,
                                      private val buildFinishedAction: PhasedBuildAction.BuildActionWrapper<Any>?) :
  TargetBuildExecuter<TargetPhasedBuildActionExecuter, Void>(environmentConfiguration, targetPathMapper, taskId, taskListener, parameters),
  BuildActionExecuter<Void> {

  override val targetBuildParametersBuilder: TargetBuildParameters.Builder
    get() = PhasedBuildActionParametersBuilder(projectsLoadedAction?.action, buildFinishedAction?.action)

  override fun run(): Void = runAndGetResult()

  @Suppress("UNCHECKED_CAST")
  override fun run(handler: ResultHandler<in Void>) = runWithHandler(handler as ResultHandler<Any?>)
  override fun getThis() = this
  override fun forTasks(vararg tasks: String) = apply { forTasks(tasks.asList()) }
  override fun forTasks(tasks: Iterable<String>) = apply { operationParamsBuilder.setTasks(tasks.toList()) }

  init {
    operationParamsBuilder.setEntryPoint("TargetPhasedBuildActionExecuter API")
  }
}