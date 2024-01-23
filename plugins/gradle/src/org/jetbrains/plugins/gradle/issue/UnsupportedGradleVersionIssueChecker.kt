// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.util.GradleBundle

/**
 * Provides the check for errors caused by dropped support in Gradle tooling API of the old Gradle versions
 *
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
class UnsupportedGradleVersionIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = getRootCauseAndLocation(issueData.error).first
    val gradleVersion = getGradleVersion(issueData)

    when {
      isGradleUnsupportedByIdea(issueData) -> {
        return UnsupportedGradleVersionIssue(gradleVersion, issueData.projectPath)
      }
      isOldGradleClasspathInfererIssue(rootCause) -> {
        return UnsupportedGradleVersionIssue(gradleVersion, issueData.projectPath)
      }
      isModelBuilderApiUnsupported(rootCause) -> {
        return UnsupportedGradleVersionIssue(gradleVersion, issueData.projectPath)
      }
      isGradleUnsupportedByToolingApi(rootCause) -> {
        return UnsupportedGradleVersionIssue(gradleVersion, issueData.projectPath)
      }
      else -> {
        return null
      }
    }
  }

  private fun getGradleVersion(issueData: GradleIssueData): GradleVersion? {
    if (issueData.buildEnvironment != null) {
      return GradleVersion.version(issueData.buildEnvironment.gradle.gradleVersion)
    }
    if (issueData.error is GradleExecutionHelper.UnsupportedGradleVersionByIdeaException) {
      return issueData.error.gradleVersion
    }
    return null
  }

  private fun isGradleUnsupportedByIdea(issueData: GradleIssueData): Boolean {
    return issueData.error is GradleExecutionHelper.UnsupportedGradleVersionByIdeaException
  }

  private fun isOldGradleClasspathInfererIssue(rootCause: Throwable): Boolean {
    val message = rootCause.message ?: return false
    if (!message.startsWith("Cannot determine classpath for resource")) return false
    return rootCause.stackTrace.find { it.className == "org.gradle.tooling.internal.provider.ClasspathInferer" } != null
  }

  private fun isModelBuilderApiUnsupported(rootCause: Throwable): Boolean {
    return rootCause.toString().endsWith(UNSUPPORTED_MODEL_BUILDER_API_EXCEPTION_TEXT)
  }

  private fun isGradleUnsupportedByToolingApi(rootCause: Throwable): Boolean {
    return rootCause.toString().startsWith(UNSUPPORTED_VERSION_EXCEPTION_MESSAGE_PREFIX)
  }

  companion object {
    private const val UNSUPPORTED_MODEL_BUILDER_API_EXCEPTION_TEXT =
      "does not support the ModelBuilder API. Support for this is available in Gradle 1.2 and all later versions."
    private const val UNSUPPORTED_VERSION_EXCEPTION_MESSAGE_PREFIX =
      "org.gradle.tooling.UnsupportedVersionException: Support for builds using Gradle versions older than "
  }
}

private class UnsupportedGradleVersionIssue(gradleVersion: GradleVersion?, projectPath: String) : AbstractGradleBuildIssue() {

  init {
    val oldestSupportedGradleVersion = GradleJvmSupportMatrix.getOldestSupportedGradleVersionByIdea()
    val oldestRecommendedGradleVersion = GradleJvmSupportMatrix.getOldestRecommendedGradleVersionByIdea()
    setTitle(GradleBundle.message("gradle.build.issue.gradle.unsupported.title"))
    if (gradleVersion == null) {
      addDescription(GradleBundle.message("gradle.build.issue.gradle.unsupported.unknown.description"))
      addDescription(GradleBundle.message("gradle.build.issue.gradle.recommended.description", oldestRecommendedGradleVersion.version))
    }
    else {
      addDescription(GradleBundle.message("gradle.build.issue.gradle.unsupported.description", gradleVersion.version))
      if (oldestSupportedGradleVersion < oldestRecommendedGradleVersion) {
        addDescription(GradleBundle.message("gradle.build.issue.gradle.recommended.description", oldestRecommendedGradleVersion.version))
      }
      addDescription(GradleBundle.message("gradle.build.issue.gradle.supported.description", oldestSupportedGradleVersion.version))
    }
    if (gradleVersion != null && oldestSupportedGradleVersion < oldestRecommendedGradleVersion) {
      addGradleVersionQuickFix(projectPath, oldestRecommendedGradleVersion)
    }
    addGradleVersionQuickFix(projectPath, oldestSupportedGradleVersion)
  }
}

@ApiStatus.Internal
class DeprecatedGradleVersionIssue(gradleVersion: GradleVersion, projectPath: String) : AbstractGradleBuildIssue() {

  init {
    val recommendedGradleVersion = GradleJvmSupportMatrix.getOldestRecommendedGradleVersionByIdea()
    setTitle(GradleBundle.message("gradle.build.issue.gradle.deprecated.title"))
    addDescription(GradleBundle.message("gradle.build.issue.gradle.deprecated.description", gradleVersion.version))
    addDescription(GradleBundle.message("gradle.build.issue.gradle.recommended.description", recommendedGradleVersion.version))
    addGradleVersionQuickFix(projectPath, recommendedGradleVersion)
  }
}