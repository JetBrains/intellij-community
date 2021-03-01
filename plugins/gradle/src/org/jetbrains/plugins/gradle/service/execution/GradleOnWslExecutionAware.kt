// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslPath
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.project.Project
import com.intellij.util.PathMapper

class GradleOnWslExecutionAware : ExternalSystemExecutionAware {
  override fun prepareExecution(
    task: ExternalSystemTask,
    externalProjectPath: String,
    isPreviewMode: Boolean,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    project: Project
  ) {
  }

  override fun getEnvironmentConfigurationProvider(runConfiguration: ExternalSystemRunConfiguration,
                                                   taskNotificationListener: ExternalSystemTaskNotificationListener,
                                                   project: Project): TargetEnvironmentConfigurationProvider? {
    val projectPath = runConfiguration.settings.externalProjectPath
    return getWslEnvironmentProvider(projectPath)
  }

  override fun getEnvironmentConfigurationProvider(projectPath: String,
                                                   isPreviewMode: Boolean,
                                                   taskNotificationListener: ExternalSystemTaskNotificationListener,
                                                   project: Project): TargetEnvironmentConfigurationProvider? {
    return getWslEnvironmentProvider(projectPath)
  }

  private fun getTargetPathMapper(projectPath: String): PathMapper? {
    val wslDistribution = resolveWslDistribution(projectPath) ?: return null
    return object : PathMapper {
      override fun isEmpty(): Boolean = false
      override fun canReplaceLocal(localPath: String): Boolean = true
      override fun convertToLocal(remotePath: String): String = wslDistribution.getWindowsPath(remotePath) ?: remotePath
      override fun canReplaceRemote(remotePath: String): Boolean = true
      override fun convertToRemote(localPath: String): String = wslDistribution.getWslPath(localPath) ?: localPath
      override fun convertToRemote(paths: MutableCollection<String>): List<String> = paths.map { convertToRemote(it) }
    }
  }

  private fun getWslEnvironmentProvider(path: String): TargetEnvironmentConfigurationProvider? {
    val wslDistribution = resolveWslDistribution(path) ?: return null
    return object : TargetEnvironmentConfigurationProvider {
      override val environmentConfiguration = WslTargetEnvironmentConfiguration(wslDistribution)
      override val pathMapper = getTargetPathMapper(path)
    }
  }

  private fun resolveWslDistribution(path: String): WSLDistribution? {
    if (!WslDistributionManager.isWslPath(path)) return null
    return WslPath.getDistributionByWindowsUncPath(path)
  }
}
