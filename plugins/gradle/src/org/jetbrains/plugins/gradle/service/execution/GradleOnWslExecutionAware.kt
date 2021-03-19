// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.execution.target.HostPort
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslPath
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemExecutionAware
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo.Resolved
import com.intellij.pom.Navigatable
import com.intellij.util.PathMapper
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.util.GradleBundle.message

@ApiStatus.Internal
class GradleOnWslExecutionAware : ExternalSystemExecutionAware {
  override fun prepareExecution(
    task: ExternalSystemTask,
    externalProjectPath: String,
    isPreviewMode: Boolean,
    taskNotificationListener: ExternalSystemTaskNotificationListener,
    project: Project
  ) {
    if (isPreviewMode) return
    val wslDistribution = resolveWslDistribution(externalProjectPath) ?: return
    // wait for JVM resolution and run common JVM checks
    val sdkInfo = GradleExecutionAware().prepareJvmForExecution(task, externalProjectPath, taskNotificationListener, project)
    if (sdkInfo !is Resolved) return
    val homePath = sdkInfo.homePath ?: return
    val jdkWslDistribution = WslPath.getDistributionByWindowsUncPath(homePath)
    if (wslDistribution.id != jdkWslDistribution?.id) {
      val isResolveProjectTask = task is ExternalSystemResolveProjectTask
      throw BuildIssueException(IncorrectGradleJdkIssue(externalProjectPath, wslDistribution, homePath, isResolveProjectTask))
    }
  }

  override fun getEnvironmentConfigurationProvider(runConfiguration: ExternalSystemRunConfiguration,
                                                   project: Project): TargetEnvironmentConfigurationProvider? {
    val projectPath = runConfiguration.settings.externalProjectPath
    return getWslEnvironmentProvider(projectPath)
  }

  override fun getEnvironmentConfigurationProvider(projectPath: String,
                                                   isPreviewMode: Boolean,
                                                   project: Project): TargetEnvironmentConfigurationProvider? {
    return getWslEnvironmentProvider(projectPath)
  }

  override fun isRemoteRun(runConfiguration: ExternalSystemRunConfiguration, project: Project): Boolean {
    val projectPath = runConfiguration.settings.externalProjectPath
    return resolveWslDistribution(projectPath) != null
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

  private fun getWslEnvironmentProvider(path: String): GradleServerConfigurationProvider? {
    val wslDistribution = resolveWslDistribution(path) ?: return null
    return object : GradleServerConfigurationProvider {
      override val environmentConfiguration = WslTargetEnvironmentConfiguration(wslDistribution)
      override val pathMapper = getTargetPathMapper(path)
      override fun getServerBindingAddress(targetEnvironmentConfiguration: TargetEnvironmentConfiguration): HostPort {
        return HostPort(wslDistribution.wslIp, 0)
      }
    }
  }

  private fun resolveWslDistribution(path: String): WSLDistribution? {
    if (!WslDistributionManager.isWslPath(path)) return null
    return WslPath.getDistributionByWindowsUncPath(path)
  }

  @ApiStatus.Internal
  class IncorrectGradleJdkIssue(projectPath: String,
                                distribution: WSLDistribution,
                                jdkHomePath: String,
                                isResolveProjectTask: Boolean) : BuildIssue {
    override val title: String = message("gradle.incorrect.jvm.issue.title")
    override val description: String
    override val quickFixes: List<BuildIssueQuickFix>
    override fun getNavigatable(project: Project): Navigatable? = null

    init {
      val gradleSettingsFix = GradleSettingsQuickFix(projectPath, isResolveProjectTask,
                                                     { oldSettings, currentSettings -> oldSettings.gradleJvm != currentSettings.gradleJvm },
                                                     message("gradle.settings.text.jvm.path"))
      quickFixes = listOf(gradleSettingsFix)
      val distributionPath = distribution.uncRootPath.toString()
      val distributionName = distribution.presentableName
      val message = message("gradle.incorrect.jvm.wsl.issue.description", jdkHomePath, distributionName, distributionPath)
      val fixLink = "<a href=\"${gradleSettingsFix.id}\">${message("gradle.open.gradle.settings")}</a>"
      description = "$message\n$fixLink\n"
    }
  }
}
