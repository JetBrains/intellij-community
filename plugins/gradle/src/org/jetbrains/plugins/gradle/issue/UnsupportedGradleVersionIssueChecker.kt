// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.util.GradleBundle

/**
 * Provides the check for errors caused by dropped support in Gradle tooling API of the old Gradle versions
 *
 * @author Vladislav.Soroka
 */
@Internal
class UnsupportedGradleVersionIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = issueData.failure.rootCause
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
    val buildEnvironment = issueData.buildEnvironment
    if (buildEnvironment != null) {
      return GradleVersion.version(buildEnvironment.gradle.gradleVersion)
    }
    @Suppress("DEPRECATION")
    val error = issueData.error
    if (error is GradleExecutionHelper.UnsupportedGradleVersionByIdeaException) {
      return error.gradleVersion
    }
    return null
  }

  private fun isGradleUnsupportedByIdea(issueData: GradleIssueData): Boolean {
    @Suppress("DEPRECATION")
    return issueData.error is GradleExecutionHelper.UnsupportedGradleVersionByIdeaException
  }

  private fun isOldGradleClasspathInfererIssue(rootCause: GradleIssueFailure): Boolean {
    val message = rootCause.messageOrDescription ?: return false
    return message.startsWith("Cannot determine classpath for resource") &&
           rootCause.description?.contains("org.gradle.tooling.internal.provider.ClasspathInferer") == true
  }

  private fun isModelBuilderApiUnsupported(rootCause: GradleIssueFailure): Boolean {
    return rootCause.text.endsWith(UNSUPPORTED_MODEL_BUILDER_API_EXCEPTION_TEXT)
  }

  private fun isGradleUnsupportedByToolingApi(rootCause: GradleIssueFailure): Boolean {
    return rootCause.text.startsWith(UNSUPPORTED_VERSION_EXCEPTION_MESSAGE_PREFIX)
  }

  companion object {
    private const val UNSUPPORTED_MODEL_BUILDER_API_EXCEPTION_TEXT =
      "does not support the ModelBuilder API. Support for this is available in Gradle 1.2 and all later versions."
    private const val UNSUPPORTED_VERSION_EXCEPTION_MESSAGE_PREFIX =
      "org.gradle.tooling.UnsupportedVersionException: Support for builds using Gradle versions older than "
  }
}

private class UnsupportedGradleVersionIssue(gradleVersion: GradleVersion?, projectPath: String) : ConfigurableGradleBuildIssue() {
  init {
    val oldestSupportedGradleVersion = GradleJvmSupportMatrix.suggestOldestSupportedGradleVersionByIdea()
    val recommendedGradleVersion = GradleJvmSupportMatrix.suggestRecommendedGradleVersionByIdea()
    setTitle(GradleBundle.message("gradle.build.issue.gradle.unsupported.title"))
    if (gradleVersion == null) {
      addDescription(GradleBundle.message("gradle.build.issue.gradle.unsupported.unknown.description"))
      addDescription(GradleBundle.message("gradle.build.issue.gradle.recommended.description", recommendedGradleVersion.version))
      addGradleVersionQuickFix(projectPath, recommendedGradleVersion)
    }
    else {
      addDescription(GradleBundle.message("gradle.build.issue.gradle.unsupported.description", gradleVersion.version))
      if (gradleVersion < oldestSupportedGradleVersion) {
        addDescription(GradleBundle.message("gradle.build.issue.gradle.supported.description", oldestSupportedGradleVersion.version))
        addGradleVersionQuickFix(projectPath, oldestSupportedGradleVersion)
      } else if (gradleVersion < recommendedGradleVersion) {
        addDescription(GradleBundle.message("gradle.build.issue.gradle.recommended.description", recommendedGradleVersion.version))
        addGradleVersionQuickFix(projectPath, recommendedGradleVersion)
      }
    }
  }
}

@Internal
class DeprecatedGradleVersionIssue(gradleVersion: GradleVersion, projectPath: String) : ConfigurableGradleBuildIssue() {
  init {
    val oldestNonDeprecatedGradleVersion = GradleJvmSupportMatrix.suggestOldestNonDeprecatedGradleVersionByIdea()
    setTitle(GradleBundle.message("gradle.build.issue.gradle.deprecated.title"))
    addDescription(GradleBundle.message("gradle.build.issue.gradle.deprecated.description", gradleVersion.version))
    addDescription(GradleBundle.message(
      "gradle.build.issue.gradle.recommended.at.least.description",
      oldestNonDeprecatedGradleVersion.version)
    )
    addGradleVersionQuickFix(projectPath, oldestNonDeprecatedGradleVersion)
  }
}