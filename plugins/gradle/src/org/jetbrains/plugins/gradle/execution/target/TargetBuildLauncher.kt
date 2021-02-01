// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.util.PathMapper
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.model.Launchable
import org.gradle.tooling.model.Task
import org.jetbrains.plugins.gradle.tooling.proxy.TargetBuildParameters

class TargetBuildLauncher(environmentConfiguration: TargetEnvironmentConfiguration,
                          targetPathMapper: PathMapper?,
                          taskId: ExternalSystemTaskId?,
                          taskListener: ExternalSystemTaskNotificationListener?,
                          parameters: ConnectionParameters) :
  TargetBuildExecuter<TargetBuildLauncher, Void>(environmentConfiguration, targetPathMapper, taskId, taskListener, parameters), BuildLauncher {

  override val targetBuildParametersBuilder
    get() = TargetBuildParameters.BuildLauncherParametersBuilder()

  override fun run() {
    runAndGetResult()
  }

  @Suppress("UNCHECKED_CAST")
  override fun run(handler: ResultHandler<in Void>) = runWithHandler(handler as ResultHandler<Any?>)
  override fun getThis(): TargetBuildLauncher = this
  override fun forTasks(vararg tasks: String) = apply { operationParamsBuilder.setTasks(tasks.asList()) }
  override fun forTasks(vararg tasks: Task) = apply { forTasks(tasks.asList()) }
  override fun forTasks(tasks: Iterable<Task>) = apply { forLaunchables(tasks) }
  override fun forLaunchables(vararg launchables: Launchable) = apply { forLaunchables(launchables.asList()) }
  override fun forLaunchables(launchables: Iterable<Launchable>) = apply { operationParamsBuilder.setLaunchables(launchables) }

  init {
    operationParamsBuilder.setEntryPoint("TargetBuildLauncher API")
  }
}
