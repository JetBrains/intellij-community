// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.externalSystem.issue.quickfix.ReimportQuickFix
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.issue.quickfix.GradleVersionQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperSettingsOpenQuickFix
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.jetbrains.plugins.gradle.util.GradleUtil

/**
 * Provides the check for errors caused by dropped support in Gradle tooling API of the old Gradle versions
 *
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
class UnsupportedGradleVersionIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = getRootCauseAndLocation(issueData.error).first
    val rootCauseText = rootCause.toString()
    var gradleVersion: GradleVersion? = null
    if (issueData.error is GradleExecutionHelper.UnsupportedGradleVersionByIdeaException) {
      gradleVersion = issueData.error.gradleVersion
    }
    if (gradleVersion == null && issueData.buildEnvironment != null) {
      gradleVersion = GradleVersion.version(issueData.buildEnvironment.gradle.gradleVersion)
    }

    val isOldGradleClasspathInfererIssue = causedByOldGradleClasspathInferer(rootCause)
    val isUnsupportedModelBuilderApi = rootCauseText.endsWith(UNSUPPORTED_MODEL_BUILDER_API_EXCEPTION_TEXT)
    val isUnsupportedByIdea = gradleVersion != null && !GradleJvmSupportMatrix.isGradleSupportedByIdea(gradleVersion)

    if (isOldGradleClasspathInfererIssue || isUnsupportedModelBuilderApi || isUnsupportedByIdea) {
      return UnsupportedGradleVersionIssue(gradleVersion, issueData.projectPath)
    }
    if (rootCauseText.startsWith(UNSUPPORTED_VERSION_EXCEPTION_MESSAGE_PREFIX)) {
      val minimumRequiredGradleVersionCandidateString = rootCauseText.substringAfter(UNSUPPORTED_VERSION_EXCEPTION_MESSAGE_PREFIX)
        .substringBefore(" ", "")
      val minimumRequiredGradleVersionCandidate = GradleInstallationManager.getGradleVersionSafe(
        minimumRequiredGradleVersionCandidateString
      )
      return UnsupportedGradleVersionIssue(gradleVersion, issueData.projectPath, minimumRequiredGradleVersionCandidate)
    }
    return null
  }

  private fun causedByOldGradleClasspathInferer(rootCause: Throwable): Boolean {
    val message = rootCause.message ?: return false
    if (!message.startsWith("Cannot determine classpath for resource")) return false
    return rootCause.stackTrace.find { it.className == "org.gradle.tooling.internal.provider.ClasspathInferer" } != null
  }

  companion object {
    private const val UNSUPPORTED_MODEL_BUILDER_API_EXCEPTION_TEXT =
      "does not support the ModelBuilder API. Support for this is available in Gradle 1.2 and all later versions."
    private const val UNSUPPORTED_VERSION_EXCEPTION_MESSAGE_PREFIX =
      "org.gradle.tooling.UnsupportedVersionException: Support for builds using Gradle versions older than "
  }
}

@ApiStatus.Internal
class UnsupportedGradleVersionIssue(
  gradleVersion: GradleVersion?,
  projectPath: String,
  minimumRequiredGradleVersionCandidate: GradleVersion? = null
) : AbstractGradleVersionIssue(gradleVersion, projectPath) {

  private val minimumSupportedGradleVersion: GradleVersion =
    GradleJvmSupportMatrix.getOldestSupportedGradleVersionByIdea()
  override val minimumRequiredGradleVersion: GradleVersion = when {
    minimumRequiredGradleVersionCandidate == null -> minimumSupportedGradleVersion
    minimumRequiredGradleVersionCandidate < minimumSupportedGradleVersion -> minimumSupportedGradleVersion
    else -> minimumRequiredGradleVersionCandidate
  }

  init {
    if (gradleVersion != null) {
      setTitle(GradleBundle.message("gradle.build.issue.gradle.unsupported.title", gradleVersion.version, ideVersionName))
      addDescription(GradleBundle.message(
        "gradle.build.issue.gradle.unsupported.description", gradleVersion.version, ideVersionName,
        minimumRequiredGradleVersion.version
      ))
    }
    else {
      setTitle(GradleBundle.message("gradle.build.issue.gradle.unsupported.title.unknown", ideVersionName))
      addDescription(GradleBundle.message(
        "gradle.build.issue.gradle.unsupported.description.unknown", ideVersionName,
        minimumRequiredGradleVersion.version
      ))
    }
    initBuildIssue()
  }
}

@ApiStatus.Internal
class DeprecatedGradleVersionIssue(
  gradleVersion: GradleVersion,
  projectPath: String
) : AbstractGradleVersionIssue(gradleVersion, projectPath) {

  override val minimumRequiredGradleVersion: GradleVersion =
    GradleJvmSupportMatrix.getOldestRecommendedGradleVersionByIdea()

  init {
    setTitle(GradleBundle.message("gradle.build.issue.gradle.deprecated.title", gradleVersion.version))
    addDescription(GradleBundle.message(
      "gradle.build.issue.gradle.deprecated.description", gradleVersion.version, ideVersionName,
      minimumRequiredGradleVersion.version
    ))

    initBuildIssue()
  }
}

@ApiStatus.Internal
abstract class AbstractGradleVersionIssue(
  private val gradleVersion: GradleVersion?,
  private val projectPath: String
) : AbstractGradleBuildIssue() {

  protected val ideVersionName: String = ApplicationInfoImpl.getShadowInstance().versionName

  protected abstract val minimumRequiredGradleVersion: GradleVersion

  protected fun initBuildIssue() {
    val wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(projectPath)
    if (wrapperPropertiesFile == null || (gradleVersion != null && gradleVersion.baseVersion < minimumRequiredGradleVersion)) {
      val gradleVersionFix = GradleVersionQuickFix(projectPath, minimumRequiredGradleVersion, true)
      addQuickFixPrompt(GradleBundle.message(
        "gradle.build.quick.fix.gradle.version.auto", gradleVersionFix.id,
        minimumRequiredGradleVersion.version
      ))
      addQuickFix(gradleVersionFix)
    }
    else {
      val wrapperSettingsOpenQuickFix = GradleWrapperSettingsOpenQuickFix(projectPath, "distributionUrl")
      val reimportQuickFix = ReimportQuickFix(projectPath, SYSTEM_ID)
      addQuickFixPrompt(GradleBundle.message(
        "gradle.build.quick.fix.gradle.version.manual", wrapperSettingsOpenQuickFix.id, reimportQuickFix.id,
        minimumRequiredGradleVersion.version
      ))
      addQuickFix(wrapperSettingsOpenQuickFix)
      addQuickFix(reimportQuickFix)
    }
  }
}
