// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.BuildConsoleUtils.getMessageTitle
import com.intellij.build.FileNavigatable
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.issue.quickfix.OpenFileQuickFix
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionErrorHandler.getRootCauseAndLocation
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.nio.file.Paths
import java.util.function.BiPredicate
import kotlin.io.path.isRegularFile

@ApiStatus.Experimental
private class GradleOutOfMemoryIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    // do not report OOM errors not related to Gradle tooling
    if (issueData.error !is org.gradle.tooling.GradleConnectionException) return null

    val rootCause = getRootCauseAndLocation(issueData.error).first
    if (rootCause !is OutOfMemoryError) return null

    val quickFixDescription = StringBuilder()
    val quickFixes = ArrayList<BuildIssueQuickFix>()
    val projectGradleProperties = Paths.get(issueData.projectPath, "gradle.properties")
    val subItemPadding = "   "
    if (projectGradleProperties.isRegularFile()) {
      val openFileQuickFix = OpenFileQuickFix(projectGradleProperties, "org.gradle.jvmargs")
      quickFixDescription.append("$subItemPadding<a href=\"${openFileQuickFix.id}\">gradle.properties</a> in project root directory\n")
      quickFixes.add(openFileQuickFix)
    }

    val gradleUserHomeDir = issueData.buildEnvironment?.gradle?.gradleUserHome ?: gradleUserHomeDir()
    val commonGradleProperties = Paths.get(gradleUserHomeDir.path, "gradle.properties")
    if (commonGradleProperties.isRegularFile()) {
      val openFileQuickFix = OpenFileQuickFix(commonGradleProperties, "org.gradle.jvmargs")
      quickFixDescription.append(
        "$subItemPadding<a href=\"${openFileQuickFix.id}\">gradle.properties</a> in GRADLE_USER_HOME directory\n")
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
      quickFixDescription.append("$subItemPadding<a href=\"${gradleSettingsFix.id}\">IDE Gradle VM options</a> \n")
    }

    val issueDescription = StringBuilder()
    if (issueData.filePosition != null) {
      val fileName = issueData.filePosition.file?.name ?: ""
      val fileTypePrefix = if (fileName.endsWith("gradle", true) || fileName.endsWith("gradle.kts", true)) "Build file " else ""
      issueDescription.appendLine("""
      * Where:
      $fileTypePrefix'${issueData.filePosition.file?.path}' line: ${issueData.filePosition.startLine + 1}

    """.trimIndent())
    }
    issueDescription.appendLine("""
      * What went wrong:
      Out of memory. ${rootCause.message}
    """.trimIndent())

    if (quickFixDescription.isNotEmpty()) {
      issueDescription.append("\nPossible solution:\n")
      issueDescription.append(" - Check the JVM memory arguments defined for the gradle process in:\n")
      issueDescription.append(quickFixDescription)
    }
    val description = issueDescription.toString()
    val title = getMessageTitle(rootCause.message ?: description)

    return object : BuildIssue {
      override val title: String = title
      override val description: String = description
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? {
        return issueData.filePosition?.run { FileNavigatable(project, issueData.filePosition) }
      }
    }
  }
}
