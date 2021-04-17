// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution.wsl

import com.intellij.execution.target.value.TargetValue
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.project.Project
import com.intellij.util.text.nullize
import org.gradle.util.DistributionLocator
import org.gradle.util.GradleVersion
import org.gradle.wrapper.WrapperConfiguration
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.plugins.gradle.execution.target.maybeGetLocalValue
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.BuildLayoutParameters
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleUtil

internal class WslBuildLayoutParameters(private val wslDistribution: WSLDistribution,
                                        private val project: Project,
                                        private val projectPath: String?) : BuildLayoutParameters {
  private val gradleProjectSettings = projectPath?.let { GradleSettings.getInstance(project).getLinkedProjectSettings(it) }

  override val gradleHome: TargetValue<String>? by lazy { findGradleHome() }
  override val gradleVersion: GradleVersion? by lazy { guessGradleVersion() }
  override val gradleUserHome: TargetValue<String> by lazy { findGradleUserHomeDir(wslDistribution) }

  private fun findGradleHome(): TargetValue<String>? {
    val distributionType = gradleProjectSettings?.distributionType ?: return null // todo add GradleHome auto-detect
    if (distributionType == DistributionType.LOCAL) return gradleProjectSettings.gradleHome?.let {
      wslDistribution.getTargetValueForRemotePath(it)
    }
    return GradleLocalSettings.getInstance(project).getGradleHome(projectPath)?.let { wslDistribution.getTargetValueForLocalPath(it) }
  }

  private fun guessGradleVersion(): GradleVersion? {
    val distributionType = gradleProjectSettings?.distributionType ?: return null
    when (distributionType) {
      DistributionType.BUNDLED -> return GradleVersion.current()
      DistributionType.LOCAL -> {
        return GradleInstallationManager.getGradleVersion(gradleHome?.maybeGetLocalValue())?.run {
          GradleInstallationManager.getGradleVersionSafe(this)
        }
      }
      DistributionType.DEFAULT_WRAPPED -> {
        val gradleVersion = GradleInstallationManager.getGradleVersion(gradleHome?.maybeGetLocalValue())?.run {
          GradleInstallationManager.getGradleVersionSafe(this)
        }
        if (gradleVersion == null) {
          val path = getWrapperConfiguration()?.distribution?.rawPath
          if (path != null) {
            return GradleInstallationManager.parseDistributionVersion(path)
          }
        }
        else {
          return gradleVersion
        }
      }
      DistributionType.WRAPPED -> return null
    }
    return null
  }

  private fun getWrapperConfiguration(): WrapperConfiguration? {
    val distributionType = gradleProjectSettings?.distributionType ?: return null
    when (distributionType) {
      DistributionType.DEFAULT_WRAPPED -> return GradleUtil.getWrapperConfiguration(projectPath)
      DistributionType.BUNDLED -> return WrapperConfiguration().apply {
        distribution = DistributionLocator().getDistributionFor(GradleVersion.current())
      }
      else -> return null
    }
  }

  private fun findGradleUserHomeDir(wslDistribution: WSLDistribution): TargetValue<String> {
    if (projectPath != null) {
      val serviceDirectoryPath = GradleSettings.getInstance(project).serviceDirectoryPath
      if (serviceDirectoryPath != null) return wslDistribution.getTargetValueForLocalPath(serviceDirectoryPath)
    }
    val gradleUserHome = wslDistribution.getEnvironmentVariable("GRADLE_USER_HOME").nullize(true)
    return wslDistribution.getTargetValueForRemotePath(gradleUserHome ?: "${wslDistribution.userHome}/.gradle")
  }

  private fun WSLDistribution.getTargetValueForLocalPath(windowsPath: String): TargetValue<String> {
    val wslPath = getWslPath(windowsPath) ?: windowsPath
    return TargetValue.create(windowsPath, resolvedPromise(wslPath))
  }

  private fun WSLDistribution.getTargetValueForRemotePath(wslPath: String): TargetValue<String> {
    val windowsPath = getWindowsPath(wslPath) ?: wslPath
    return TargetValue.create(windowsPath, resolvedPromise(wslPath))
  }
}