// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution.wsl

import com.intellij.execution.target.value.TargetValue
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.project.Project
import com.intellij.util.text.nullize
import org.gradle.util.GradleVersion
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.plugins.gradle.execution.target.maybeGetLocalValue
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.BuildLayoutParameters
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path

internal class WslBuildLayoutParameters(private val wslDistribution: WSLDistribution,
                                        private val project: Project,
                                        private val projectPath: String?) : BuildLayoutParameters {
  private val gradleProjectSettings = projectPath?.let { GradleSettings.getInstance(project).getLinkedProjectSettings(it) }

  override val gradleHome: TargetValue<Path>? by lazy { findGradleHome() }
  override val gradleVersion: GradleVersion? by lazy { guessGradleVersion() }
  override val gradleUserHomePath: TargetValue<Path> by lazy { findGradleUserHomeDir(wslDistribution) }

  private fun findGradleHome(): TargetValue<Path>? {
    val distributionType = gradleProjectSettings?.distributionType ?: return null // todo add GradleHome auto-detect
    if (distributionType == DistributionType.LOCAL) return gradleProjectSettings.gradleHome?.let {
      wslDistribution.getTargetValueForRemotePath(it)
    }
    val gradleUserHome = GradleLocalSettings.getInstance(project).getGradleHome(projectPath)?.let { Path.of(it) } ?: return null
    return wslDistribution.getTargetValueForLocalPath(gradleUserHome)
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
          val path = GradleUtil.getWrapperConfiguration(projectPath)?.distribution?.rawPath
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

  private fun findGradleUserHomeDir(wslDistribution: WSLDistribution): TargetValue<Path> {
    if (projectPath != null) {
      val serviceDirectoryPath = GradleSettings.getInstance(project).serviceDirectoryPath?.let { Path.of(it) }
      if (serviceDirectoryPath != null) return wslDistribution.getTargetValueForLocalPath(serviceDirectoryPath)
    }
    val gradleUserHome = wslDistribution.getEnvironmentVariable("GRADLE_USER_HOME").nullize(true)
    return wslDistribution.getTargetValueForRemotePath(gradleUserHome ?: "${wslDistribution.userHome}/.gradle")
  }

  private fun WSLDistribution.getTargetValueForLocalPath(windowsPath: Path): TargetValue<Path> {
    val wslPath = getWslPath(windowsPath)?.let { Path.of(it) } ?: windowsPath
    return TargetValue.create(windowsPath, resolvedPromise(wslPath))
  }

  private fun WSLDistribution.getTargetValueForRemotePath(wslPath: String): TargetValue<Path> {
    val windowsPath = Path.of(getWindowsPath(wslPath))
    return TargetValue.create(windowsPath, resolvedPromise(Path.of(wslPath)))
  }
}