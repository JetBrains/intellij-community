// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.BuildConsoleUtils.getMessageTitle
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.issue.quickfix.OpenFileQuickFix
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.util.PlatformUtils
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.execution.GradleConsoleFilter
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.nio.file.Paths
import java.util.function.BiPredicate
import java.util.function.Consumer
import kotlin.io.path.isRegularFile

/**
 * This issue checker provides quick fixes to deal with known startup issues of the Gradle daemon.
 */
@ApiStatus.Experimental
private class GradleDaemonStartupIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = getRootCauseAndLocation(issueData.error).first
    val rootCauseText = rootCause.toString()
    if (!rootCauseText.startsWith("org.gradle.api.GradleException: Unable to start the daemon process.")) {
      return null
    }

    val quickFixDescription = StringBuilder()
    val quickFixes = ArrayList<BuildIssueQuickFix>()
    val projectGradleProperties = Paths.get(issueData.projectPath, "gradle.properties")
    if (projectGradleProperties.isRegularFile()) {
      val openFileQuickFix = OpenFileQuickFix(projectGradleProperties, "org.gradle.jvmargs")
      quickFixDescription.append(" - <a href=\"${openFileQuickFix.id}\">gradle.properties</a> in project root directory\n")
      quickFixes.add(openFileQuickFix)
    }

    val gradleUserHomeDir = issueData.buildEnvironment?.gradle?.gradleUserHome ?: gradleUserHomeDir()
    val commonGradleProperties = Paths.get(gradleUserHomeDir.path, "gradle.properties")
    if (commonGradleProperties.isRegularFile()) {
      val openFileQuickFix = OpenFileQuickFix(commonGradleProperties, "org.gradle.jvmargs")
      quickFixDescription.append(" - <a href=\"${openFileQuickFix.id}\">gradle.properties</a> in GRADLE_USER_HOME directory\n")
      quickFixes.add(openFileQuickFix)
    }

    val gradleVmOptions = GradleSystemSettings.getInstance().gradleVmOptions
    if (!gradleVmOptions.isNullOrBlank() && "AndroidStudio" != PlatformUtils.getPlatformPrefix()) { // Android Studio doesn't have Gradle VM options setting
      val gradleSettingsFix = GradleSettingsQuickFix(
        issueData.projectPath, true,
        BiPredicate { _, _ -> gradleVmOptions != GradleSystemSettings.getInstance().gradleVmOptions },
        GradleBundle.message("gradle.settings.text.vm.options")
      )
      quickFixes.add(gradleSettingsFix)
      quickFixDescription.append(" - <a href=\"${gradleSettingsFix.id}\">IDE Gradle VM options</a> \n")
    }

    val issueDescription = StringBuilder(rootCause.message)
    if (quickFixDescription.isNotEmpty()) {
      issueDescription.append("\n-----------------------\n")
      issueDescription.append("Check the JVM arguments defined for the gradle process in:\n")
      issueDescription.append(quickFixDescription)
    }

    val description = issueDescription.toString()
    val title = getMessageTitle(description)
    return object : BuildIssue {
      override val title: String = title
      override val description: String = description
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }

  override fun consumeBuildOutputFailureMessage(message: String,
                                                failureCause: String,
                                                stacktrace: String?,
                                                location: FilePosition?,
                                                parentEventId: Any,
                                                messageConsumer: Consumer<in BuildEvent>): Boolean {
    if (location == null) return false

    if (failureCause == "startup failed:") {
      val locationLine: @Nls String = message.substringAfter("> startup failed:", "").nullize()?.trimStart()?.substringBefore("\n") ?: return false
      val failedStartupReason: @Nls String  = locationLine.substringAfter("'${location.file?.path ?: ""}': ${location.startLine + 1}: ", "") //NON-NLS
                                  .nullize()?.substringBeforeLast(" @ ") ?: return false //NON-NLS
      val locationPart = locationLine.substringAfterLast(" @ ")
      val matchResult = GradleConsoleFilter.LINE_AND_COLUMN_PATTERN.toRegex().matchEntire(locationPart)
      val values = matchResult?.groupValues?.drop(1)?.map { it.toInt() } ?: listOf(location.startLine + 1, 0)
      val line = values[0] - 1
      val column = values[1]

      messageConsumer.accept(object : FileMessageEventImpl(
        parentEventId, MessageEvent.Kind.ERROR, null, failedStartupReason, message, //NON-NLS
        FilePosition(location.file, line, column)), DuplicateMessageAware {}
      )
      return true
    }

    return false
  }
}
