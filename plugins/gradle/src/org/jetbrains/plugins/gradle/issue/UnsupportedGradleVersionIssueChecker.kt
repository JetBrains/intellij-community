// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.BuildConsoleUtils.getMessageTitle
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.externalSystem.issue.quickfix.ReimportQuickFix
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.issue.quickfix.GradleVersionQuickFix
import org.jetbrains.plugins.gradle.issue.quickfix.GradleWrapperSettingsOpenQuickFix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.util.GradleConstants.MINIMAL_SUPPORTED_GRADLE_VERSION
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
    var gradleVersionUsed: GradleVersion? = null
    if (issueData.buildEnvironment != null) {
      gradleVersionUsed = GradleVersion.version(issueData.buildEnvironment.gradle.gradleVersion)
    }

    val isOldGradleClasspathInfererIssue = causedByOldGradleClasspathInferer(gradleVersionUsed, rootCause)

    val isAncientGradleVersion =
      isOldGradleClasspathInfererIssue ||
      rootCauseText.endsWith(
        "does not support the ModelBuilder API. Support for this is available in Gradle 1.2 and all later versions.") ||
      gradleVersionUsed?.let { it < GradleVersion.version(MINIMAL_SUPPORTED_GRADLE_VERSION) } == true

    val unsupportedVersionMessagePrefix = "org.gradle.tooling.UnsupportedVersionException: Support for builds using Gradle versions older than "
    if (!isAncientGradleVersion && !rootCauseText.startsWith(unsupportedVersionMessagePrefix)) {
      return null
    }

    val minRequiredVersionCandidate: String
    if (isAncientGradleVersion) minRequiredVersionCandidate = MINIMAL_SUPPORTED_GRADLE_VERSION
    else minRequiredVersionCandidate = rootCauseText.substringAfter(unsupportedVersionMessagePrefix).substringBefore(" ", "")
    val gradleMinimumVersionRequired = try {
      GradleVersion.version(minRequiredVersionCandidate)
    }
    catch (e: IllegalArgumentException) {
      GradleVersion.current()
    }

    return UnsupportedGradleVersionIssue(gradleVersionUsed, issueData.projectPath, gradleMinimumVersionRequired)
  }

  companion object {
    private fun causedByOldGradleClasspathInferer(gradleVersionUsed: GradleVersion?, rootCause: Throwable): Boolean {
      val message = rootCause.message ?: return false
      if (!message.startsWith("Cannot determine classpath for resource")) return false
      if (gradleVersionUsed == null) {
        return rootCause.stackTrace.find { it.className == "org.gradle.tooling.internal.provider.ClasspathInferer" } != null
      }
      else
        return (gradleVersionUsed.baseVersion ?: return true) < GradleVersion.version(MINIMAL_SUPPORTED_GRADLE_VERSION)
    }
  }
}

class UnsupportedGradleVersionIssue(gradleVersionUsed: GradleVersion?,
                                    projectPath: String,
                                    gradleMinimumVersionRequired: GradleVersion) : BuildIssue {

  override val title: String
  override val description: String
  override val quickFixes: List<BuildIssueQuickFix>
  init {
    val quickFixes1 = mutableListOf<BuildIssueQuickFix>()
    val issueDescription = StringBuilder()
    val gradleVersionString = if (gradleVersionUsed != null) gradleVersionUsed.version else "version"

    val appInfo = ApplicationInfoImpl.getShadowInstance()
    val ideVersion = "${appInfo.versionName} ${appInfo.majorVersion}.${appInfo.minorVersion}"
    issueDescription
      .append("Unsupported Gradle. \n") // title
      .append("The project uses Gradle $gradleVersionString which is incompatible with $ideVersion.\n")

    issueDescription.append("\nPossible solution:\n")
    val wrapperPropertiesFile = GradleUtil.findDefaultWrapperPropertiesFile(projectPath)
    if (wrapperPropertiesFile == null || gradleVersionUsed != null && gradleVersionUsed.baseVersion < gradleMinimumVersionRequired) {
      val gradleVersionFix = GradleVersionQuickFix(projectPath, gradleMinimumVersionRequired, true)
      issueDescription.append(
        " - <a href=\"${gradleVersionFix.id}\">Upgrade Gradle wrapper to ${gradleMinimumVersionRequired.version} version " +
        "and re-import the project</a>\n")
      quickFixes1.add(gradleVersionFix)
    }
    else {
      val wrapperSettingsOpenQuickFix = GradleWrapperSettingsOpenQuickFix(projectPath, "distributionUrl")
      val reimportQuickFix = ReimportQuickFix(projectPath, SYSTEM_ID)
      issueDescription.append(" - <a href=\"${wrapperSettingsOpenQuickFix.id}\">Open Gradle wrapper settings</a>, " +
                              "upgrade version to ${gradleMinimumVersionRequired.version} or newer and <a href=\"${reimportQuickFix.id}\">reload the project</a>\n")
      quickFixes1.add(wrapperSettingsOpenQuickFix)
      quickFixes1.add(reimportQuickFix)
    }

    description = issueDescription.toString()
    title = getMessageTitle(description)
    quickFixes = quickFixes1
  }
  override fun getNavigatable(project: Project): Navigatable? = null

  companion object {
    private val minimalSupportedVersion: GradleVersion = GradleVersion.version(MINIMAL_SUPPORTED_GRADLE_VERSION)
    @JvmStatic
    fun isUnsupported(gradleVersion: GradleVersion): Boolean {
      return gradleVersion < minimalSupportedVersion
    }
  }
}
