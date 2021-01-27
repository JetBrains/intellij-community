// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.Project

class GradleOnWslExecutionAware : ExternalSystemExecutionAware {
  override fun prepareExecution(
    task: ExternalSystemTask,
    externalProjectPath: String,
    isPreviewMode: Boolean,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    project: Project
  ) {
  }

  override fun getEnvironmentConfiguration(runConfiguration: ExternalSystemRunConfiguration,
                                           taskNotificationListener: ExternalSystemTaskNotificationListener,
                                           project: Project): TargetEnvironmentConfiguration? {
    val projectPath = runConfiguration.settings.externalProjectPath
    return resolveWslEnvironment(projectPath)
  }

  override fun getEnvironmentConfiguration(externalProjectPath: String,
                                           isPreviewMode: Boolean,
                                           taskNotificationListener: ExternalSystemTaskNotificationListener,
                                           project: Project): TargetEnvironmentConfiguration? {
    return resolveWslEnvironment(externalProjectPath)
  }

  private fun resolveWslEnvironment(path: String): TargetEnvironmentConfiguration? {
    val pathInWsl = WslDistributionManager.getInstance().parseWslPath(path)
    val wslDistribution = pathInWsl?.second ?: return null
    return wslDistribution.let { WslTargetEnvironmentConfiguration(it).withJavaRuntime() }
  }
}

private fun WslTargetEnvironmentConfiguration.withJavaRuntime(): WslTargetEnvironmentConfiguration {
  if (runtimes.findByType(JavaLanguageRuntimeConfiguration::class.java) != null) return this
  addLanguageRuntime(JavaLanguageRuntimeConfiguration().apply { homePath = "/usr" })
  return this
}
