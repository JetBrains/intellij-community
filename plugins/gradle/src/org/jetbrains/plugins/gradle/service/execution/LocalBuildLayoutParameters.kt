// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.io.toNioPathOrNull
import org.gradle.util.GradleVersion
import org.gradle.wrapper.PathAssembler
import org.gradle.wrapper.WrapperConfiguration
import org.jetbrains.plugins.gradle.execution.target.maybeGetLocalValue
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

internal open class LocalBuildLayoutParameters(
  private val project: Project,
  private val projectPath: Path?,
) : BuildLayoutParameters {
  override val gradleHome: TargetValue<Path>? by lazy { findGradleHome()?.let { TargetValue.fixed(it) } }
  override val gradleVersion: GradleVersion? by lazy { guessGradleVersion() }
  override val gradleUserHomePath: TargetValue<Path> by lazy { TargetValue.fixed(findGradleUserHomeDir()) }

  protected open fun getGradleProjectSettings(): GradleProjectSettings? {
    return projectPath?.let { getGradleSettings().getLinkedProjectSettings(it.toCanonicalPath()) }
  }

  private fun getGradleSettings() = GradleSettings.getInstance(project)

  private val wrapperConfiguration: WrapperConfiguration? by lazy {
    val distributionType = getGradleProjectSettings()?.distributionType ?: return@lazy null
    when (distributionType) {
      DistributionType.DEFAULT_WRAPPED -> GradleUtil.getWrapperConfiguration(projectPath)
      DistributionType.BUNDLED -> WrapperConfiguration().apply {
        distribution = GradleUtil.getWrapperDistributionUri(GradleVersion.current())
      }
      else -> null
    }
  }

  private fun findGradleHome(): Path? {
    val gradleProjectSettings = getGradleProjectSettings() ?: return null
    return when (gradleProjectSettings.distributionType) {
      null -> GradleInstallationManager.getInstance().getAutodetectedGradleHome(project)?.toPath()
      DistributionType.LOCAL -> gradleProjectSettings.gradleHome?.toNioPathOrNull()
      DistributionType.WRAPPED -> {
        val projectNioPath = projectPath?.toCanonicalPath()
        val localSettings = GradleLocalSettings.getInstance(project)
        return localSettings.getGradleHome(projectNioPath)?.toNioPathOrNull()
      }
      else -> tryToFindGradleInstallation(gradleProjectSettings)
    }
  }

  private fun tryToFindGradleInstallation(gradleProjectSettings: GradleProjectSettings): Path? {
    if (wrapperConfiguration == null) return null
    val localGradleUserHome = gradleUserHomePath.maybeGetLocalValue() ?: return null
    val localDistribution = PathAssembler(localGradleUserHome.toFile(), File(gradleProjectSettings.externalProjectPath))
      .getDistribution(wrapperConfiguration)
    val distributionDir = localDistribution.distributionDir ?: return null
    if (!distributionDir.exists()) return null
    try {
      val dirs = Files.list(distributionDir.toPath()).use { it.filter { it.isDirectory() }.unordered().limit(2).toList() }
      if (dirs.size == 1) {
        // Expected to find exactly 1 directory, see org.gradle.wrapper.Install.verifyDistributionRoot
        return dirs.first()
      }
    }
    catch (e: Exception) {
      log.debug("Can not find Gradle installation inside ${distributionDir.path}", e)
    }
    return null
  }

  private fun guessGradleVersion(): GradleVersion? {
    val distributionType = getGradleProjectSettings()?.distributionType ?: return null
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
          val path = wrapperConfiguration?.distribution?.rawPath
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

  private fun findGradleUserHomeDir(): Path {
    if (projectPath == null) return defaultGradleUserHome()
    return getGradleSettings().serviceDirectoryPath?.toNioPathOrNull() ?: defaultGradleUserHome()
  }

  private fun defaultGradleUserHome() = gradleUserHomeDir().toPath()

  companion object {
    private val log = logger<LocalGradleExecutionAware>()
  }
}