// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.target.value.TargetValue
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.gradle.util.DistributionLocator
import org.gradle.util.GradleVersion
import org.gradle.wrapper.PathAssembler
import org.gradle.wrapper.WrapperConfiguration
import org.jetbrains.plugins.gradle.execution.target.maybeGetLocalValue
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.io.File
import java.nio.file.Files
import kotlin.io.path.isDirectory

internal open class LocalBuildLayoutParameters(private val project: Project,
                                               private val projectPath: String?) : BuildLayoutParameters {
  override val gradleHome: TargetValue<String>? by lazy { findGradleHome()?.let { TargetValue.fixed(it) } }
  override val gradleVersion: GradleVersion? by lazy { guessGradleVersion() }
  override val gradleUserHome: TargetValue<String> by lazy { TargetValue.fixed(findGradleUserHomeDir()) }

  protected open fun getGradleProjectSettings() = projectPath?.let { getGradleSettings().getLinkedProjectSettings(it) }
  private fun getGradleSettings() = GradleSettings.getInstance(project)

  private val wrapperConfiguration: WrapperConfiguration? by lazy {
    val distributionType = getGradleProjectSettings()?.distributionType ?: return@lazy null
    when (distributionType) {
      DistributionType.DEFAULT_WRAPPED -> GradleUtil.getWrapperConfiguration(projectPath)
      DistributionType.BUNDLED -> WrapperConfiguration().apply {
        distribution = DistributionLocator().getDistributionFor(GradleVersion.current())
      }
      else -> null
    }
  }

  private fun findGradleHome(): String? {
    val gradleProjectSettings = getGradleProjectSettings()
    val distributionType = gradleProjectSettings?.distributionType
                           ?: return GradleInstallationManager.getInstance().getAutodetectedGradleHome(project)?.path
    if (distributionType == DistributionType.LOCAL) return gradleProjectSettings.gradleHome
    if (distributionType == DistributionType.WRAPPED) return GradleLocalSettings.getInstance(project).getGradleHome(projectPath)

    if (wrapperConfiguration == null) return null
    val localGradleUserHome = gradleUserHome.maybeGetLocalValue() ?: return null
    val localDistribution = PathAssembler(File(localGradleUserHome), File(gradleProjectSettings.externalProjectPath))
      .getDistribution(wrapperConfiguration)
    val distributionDir = localDistribution.distributionDir ?: return null
    if (!distributionDir.exists()) return null
    try {
      val dirs = Files.list(distributionDir.toPath()).use { it.filter { it.isDirectory() }.unordered().limit(2).toList() }
      if (dirs.size == 1) {
        // Expected to find exactly 1 directory, see org.gradle.wrapper.Install.verifyDistributionRoot
        return dirs.first().toString()
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

  private fun findGradleUserHomeDir(): String {
    if (projectPath == null) return defaultGradleUserHome()
    return getGradleSettings().serviceDirectoryPath ?: defaultGradleUserHome()
  }

  private fun defaultGradleUserHome() = gradleUserHomeDir().path

  companion object {
    private val log = logger<LocalGradleExecutionAware>()
  }
}