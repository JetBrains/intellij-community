// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.util.lang.JavaVersion
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.io.File
import java.util.function.Consumer

/**
 * This issue checker provides quick fixes for compatibility issues with Gradle and Java.
 *
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
class IncompatibleGradleJvmAndGradleIssueChecker : GradleIssueChecker {

  override fun consumeBuildOutputFailureMessage(
    message: String,
    failureCause: String,
    stacktrace: String?,
    location: FilePosition?,
    parentEventId: Any,
    messageConsumer: Consumer<in BuildEvent>
  ): Boolean {
    // JDK compatibility issues should be handled by IncompatibleGradleJdkIssueChecker.check method based on exceptions come from Gradle TAPI
    return failureCause.startsWith(COULD_NOT_CREATE_SERVICE_OF_TYPE_PREFIX) &&
           failureCause.contains(USING_BUILD_SCOPE_SERVICES)
  }

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = getRootCauseAndLocation(issueData.error).first
    val gradleVersion = getGradleVersion(issueData)
    val javaVersion = getJavaVersion(issueData)

    when {
      gradleVersion != null && javaVersion != null -> {
        if (!GradleJvmSupportMatrix.isSupported(gradleVersion, javaVersion)) {
          return IncompatibleGradleJvmAndGradleBuildIssue(gradleVersion, javaVersion, issueData.projectPath)
        }
      }
      isCouldNotDetermineJavaIssue(rootCause) -> {
        val detectedJavaVersion = detectJavaVersionIfCouldNotDetermineJavaIssue(rootCause) ?: javaVersion
        return IncompatibleGradleJvmAndGradleBuildIssue(gradleVersion, detectedJavaVersion, issueData.projectPath)
      }
      isUnsupportedClassVersionIssue(rootCause) -> {
        return IncompatibleGradleJvmAndGradleBuildIssue(gradleVersion, javaVersion, issueData.projectPath)
      }
      isUnsupportedJavaRuntimeIssue(rootCause) -> {
        return IncompatibleGradleJvmAndGradleBuildIssue(gradleVersion, javaVersion, issueData.projectPath)
      }
    }
    return null
  }

  private fun getGradleVersion(issueData: GradleIssueData): GradleVersion? {
    if (issueData.buildEnvironment != null) {
      return GradleVersion.version(issueData.buildEnvironment.gradle.gradleVersion)
    }
    return null
  }

  private fun getJavaVersion(issueData: GradleIssueData): JavaVersion? {
    if (issueData.buildEnvironment != null) {
      return ExternalSystemJdkUtil.getJavaVersion(issueData.buildEnvironment.java.javaHome.path)
    }
    return null
  }

  private fun isUnsupportedClassVersionIssue(rootCause: Throwable): Boolean {
    return rootCause.javaClass.simpleName == UnsupportedClassVersionError::class.java.simpleName
  }

  private fun isUnsupportedJavaRuntimeIssue(rootCause: Throwable): Boolean {
    return rootCause.javaClass.simpleName == UnsupportedJavaRuntimeException::class.java.simpleName
  }

  /**
   * Gradle versions less than 4.7 do not support JEP-322 (Java starting with JDK 10-ea build 36),
   * see https://github.com/gradle/gradle/issues/4503
   */
  private fun isCouldNotDetermineJavaIssue(rootCause: Throwable): Boolean {
    val rootCauseText = rootCause.toString()
    if (rootCauseText.startsWith(COULD_NOT_DETERMINE_JAVA_USING_EXECUTABLE_PREFIX)) {
      return true
    }
    if (rootCause.message == COULD_NOT_DETERMINE_JAVA_VERSION_MESSAGE) {
      return true
    }
    return false
  }

  private fun detectJavaVersionIfCouldNotDetermineJavaIssue(rootCause: Throwable): JavaVersion? {
    val rootCauseText = rootCause.toString()
    val javaExeCandidate = rootCauseText.substringAfter(COULD_NOT_DETERMINE_JAVA_USING_EXECUTABLE_PREFIX).trimEnd('.')
    val javaHome = File(javaExeCandidate).parentFile?.parentFile
    if (javaHome != null && javaHome.isDirectory) {
      return ExternalSystemJdkUtil.getJavaVersion(javaHome.path)
    }
    return null
  }

  companion object {

    private const val COULD_NOT_CREATE_SERVICE_OF_TYPE_PREFIX =
      "Could not create service of type "

    private const val USING_BUILD_SCOPE_SERVICES =
      " using BuildScopeServices."

    private const val COULD_NOT_DETERMINE_JAVA_VERSION_MESSAGE =
      "Could not determine Java version."

    private const val COULD_NOT_DETERMINE_JAVA_USING_EXECUTABLE_PREFIX =
      "org.gradle.api.GradleException: Could not determine Java version using executable "
  }
}

private class IncompatibleGradleJvmAndGradleBuildIssue(
  gradleVersion: GradleVersion?,
  javaVersion: JavaVersion?,
  projectPath: String
) : ConfigurableGradleBuildIssue() {

  init {
    val oldestCompatibleJavaVersion = gradleVersion?.let { GradleJvmSupportMatrix.suggestOldestSupportedJavaVersion(it) }
    val latestCompatibleJavaVersion = gradleVersion?.let { GradleJvmSupportMatrix.suggestLatestSupportedJavaVersion(it) }
    val oldestCompatibleGradleVersion = javaVersion?.let { GradleJvmSupportMatrix.suggestOldestSupportedGradleVersion(it) }
    val latestCompatibleGradleVersion = javaVersion?.let { GradleJvmSupportMatrix.suggestLatestSupportedGradleVersion(it) }
    val recommendedGradleVersion = GradleJvmSupportMatrix.getRecommendedGradleVersionByIdea()
    setTitle(GradleBundle.message("gradle.build.issue.gradle.jvm.incompatible.title"))
    when {
      javaVersion == null -> {
        addDescription(GradleBundle.message("gradle.build.issue.gradle.jvm.incompatible.unknown.java.description"))
      }
      gradleVersion == null -> {
        addDescription(GradleBundle.message("gradle.build.issue.gradle.jvm.incompatible.unknown.gradle.description", javaVersion))
      }
      else -> {
        addDescription(GradleBundle.message("gradle.build.issue.gradle.jvm.incompatible.description", javaVersion, gradleVersion.version))
      }
    }
    when {
      gradleVersion != null && oldestCompatibleGradleVersion != null && gradleVersion < oldestCompatibleGradleVersion -> {
        if (gradleVersion < recommendedGradleVersion && GradleJvmSupportMatrix.isSupported(recommendedGradleVersion, javaVersion)) {
          addDescription(GradleBundle.message("gradle.build.issue.gradle.recommended.description", recommendedGradleVersion.version))
          addGradleVersionQuickFix(projectPath, recommendedGradleVersion)
        }
        if (oldestCompatibleGradleVersion < recommendedGradleVersion) {
          addDescription(GradleBundle.message("gradle.build.issue.gradle.compatible.minimum.description", oldestCompatibleGradleVersion.version))
          addGradleVersionQuickFix(projectPath, oldestCompatibleGradleVersion)
        }
      }
      gradleVersion != null && latestCompatibleGradleVersion != null && gradleVersion > latestCompatibleGradleVersion -> {
        addDescription(GradleBundle.message("gradle.build.issue.gradle.compatible.maximum.description", latestCompatibleGradleVersion.version))
        addGradleVersionQuickFix(projectPath, latestCompatibleGradleVersion)
      }
    }
    when {
      javaVersion != null && oldestCompatibleJavaVersion != null && javaVersion < oldestCompatibleJavaVersion -> {
        addDescription(GradleBundle.message("gradle.build.issue.gradle.jvm.compatible.minimum.description", oldestCompatibleJavaVersion))
        addGradleJvmQuickFix(projectPath, oldestCompatibleJavaVersion)
      }
      javaVersion != null && latestCompatibleJavaVersion != null && javaVersion > latestCompatibleJavaVersion -> {
        addDescription(GradleBundle.message("gradle.build.issue.gradle.jvm.compatible.maximum.description", latestCompatibleJavaVersion))
        addGradleJvmQuickFix(projectPath, latestCompatibleJavaVersion)
      }
    }
  }
}
