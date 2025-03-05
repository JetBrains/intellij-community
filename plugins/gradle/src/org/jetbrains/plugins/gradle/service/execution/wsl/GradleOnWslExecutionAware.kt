// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution.wsl

import com.intellij.execution.target.HostPort
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentsManager
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WSLUtil
import com.intellij.execution.wsl.WslPath
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo.Resolved
import com.intellij.util.PathMapper
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.issue.IncorrectGradleJdkIssue
import org.jetbrains.plugins.gradle.service.execution.*
import org.jetbrains.plugins.gradle.util.GradleBundle.message
import java.nio.file.Path

@ApiStatus.Internal
class GradleOnWslExecutionAware : GradleExecutionAware {
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
    val sdkInfo = LocalGradleExecutionAware().prepareJvmForExecution(task, externalProjectPath, taskNotificationListener, project)
    if (sdkInfo !is Resolved) return
    val homePath = sdkInfo.homePath ?: return
    val jdkWslDistribution = WslPath.getDistributionByWindowsUncPath(homePath)
    if (wslDistribution.id != jdkWslDistribution?.id) {
      val isResolveProjectTask = task is ExternalSystemResolveProjectTask
      val distributionPath = wslDistribution.getUNCRootPath().toString()
      val distributionName = wslDistribution.presentableName
      val message = message("gradle.incorrect.jvm.wsl.issue.description", distributionName, distributionPath)
      throw BuildIssueException(IncorrectGradleJdkIssue(externalProjectPath, homePath, message, isResolveProjectTask))
    }
  }

  override fun getEnvironmentConfigurationProvider(runConfiguration: ExternalSystemRunConfiguration,
                                                   project: Project): TargetEnvironmentConfigurationProvider? {
    val projectPath = runConfiguration.settings.externalProjectPath
    val gradleRunConfiguration = runConfiguration as? GradleRunConfiguration ?: return null
    val targetName = gradleRunConfiguration.options.remoteTarget
    return getWslEnvironmentProvider(project, projectPath, targetName)
  }

  override fun getEnvironmentConfigurationProvider(projectPath: String,
                                                   isPreviewMode: Boolean,
                                                   project: Project): TargetEnvironmentConfigurationProvider? {
    return getWslEnvironmentProvider(project, projectPath, null)
  }

  override fun isRemoteRun(runConfiguration: ExternalSystemRunConfiguration, project: Project): Boolean {
    val projectPath = runConfiguration.settings.externalProjectPath
    return resolveWslDistribution(projectPath) != null
  }

  override fun getDefaultBuildLayoutParameters(project: Project): BuildLayoutParameters? {
    val projectLocation = project.guessProjectDir()?.path ?: return null
    val wslDistribution = resolveWslDistribution(projectLocation) ?: return null
    return WslBuildLayoutParameters(wslDistribution, project, null)
  }

  override fun getBuildLayoutParameters(project: Project, projectPath: Path): BuildLayoutParameters? {
    val projectPathString = projectPath.toString()
    val wslDistribution = resolveWslDistribution(projectPathString) ?: return null
    return WslBuildLayoutParameters(wslDistribution, project, projectPathString)
  }

  override fun isGradleInstallationHomeDir(project: Project, homePath: Path): Boolean {
    val wslDistribution = resolveWslDistribution(project.basePath ?: return false) ?: return false
    val windowsPath = wslDistribution.getWindowsPath(homePath.toString())
    return LocalGradleExecutionAware().isGradleInstallationHomeDir(project, Path.of(windowsPath))
  }

  private fun getTargetPathMapper(wslDistribution: WSLDistribution): PathMapper {
    return object : PathMapper {
      override fun isEmpty(): Boolean = false
      override fun canReplaceLocal(localPath: String): Boolean = true
      override fun convertToLocal(remotePath: String): String = wslDistribution.getWindowsPath(remotePath) ?: remotePath
      override fun canReplaceRemote(remotePath: String): Boolean = true
      override fun convertToRemote(localPath: String): String = wslDistribution.getWslPath(localPath) ?: localPath
      override fun convertToRemote(paths: MutableCollection<String>): List<String> = paths.map { convertToRemote(it) }
    }
  }

  private fun getWslEnvironmentProvider(project: Project, path: String, targetName: String?): GradleServerConfigurationProvider? {
    val wslEnvironmentConfiguration = targetName?.let {
      TargetEnvironmentsManager.getInstance(project).targets.findByName(it)
    } as? WslTargetEnvironmentConfiguration

    val wslDistribution = wslEnvironmentConfiguration?.distribution ?: resolveWslDistribution(path) ?: return null
    return object : GradleServerConfigurationProvider {
      override val environmentConfiguration by lazy {
        wslEnvironmentConfiguration ?: WslTargetEnvironmentConfiguration(wslDistribution)
      }
      override val pathMapper = getTargetPathMapper(wslDistribution)
      override fun getServerBindingAddress(targetEnvironmentConfiguration: TargetEnvironmentConfiguration): HostPort {
        return HostPort(wslDistribution.wslIpAddress.hostAddress, 0)
      }
    }
  }

  private fun resolveWslDistribution(path: String): WSLDistribution? {
    if (!WSLUtil.isSystemCompatible()) return null
    return WslPath.getDistributionByWindowsUncPath(path)
  }
}
