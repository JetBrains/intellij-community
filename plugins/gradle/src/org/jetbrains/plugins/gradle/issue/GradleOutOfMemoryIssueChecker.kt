// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.BuildConsoleUtils.getMessageTitle
import com.intellij.build.FileNavigatable
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.issue.quickfix.OpenFileQuickFix
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.pom.Navigatable
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.function.BiPredicate
import kotlin.io.path.isRegularFile

@Internal
class GradleOutOfMemoryIssueChecker : GradleIssueChecker {
  override fun check(issueData: GradleIssueData): BuildIssue? {
    val rootCause = issueData.failure.rootCause
    if (rootCause.className != OutOfMemoryError::class.java.name &&
        rootCause.message != "Java heap space" &&
        rootCause.message != "GC overhead limit exceeded" &&
        rootCause.description?.lineSequence()?.firstOrNull()?.startsWith(OutOfMemoryError::class.java.name) != true) {
      return null
    }
    val rootCauseMessage = rootCause.messageOrDescription
    val filePosition = issueData.filePosition

    val quickFixDescription = StringBuilder()
    val quickFixes = ArrayList<BuildIssueQuickFix>()
    val projectGradleProperties = issueData.projectRoot.resolve("gradle.properties")
    val subItemPadding = "   "
    if (projectGradleProperties.isRegularFile()) {
      val openFileQuickFix = OpenFileQuickFix(projectGradleProperties, "org.gradle.jvmargs")
      quickFixDescription.append("$subItemPadding<a href=\"${openFileQuickFix.id}\">gradle.properties</a> in project root directory\n")
      quickFixes.add(openFileQuickFix)
    }

    val gradleUserHomeDir = issueData.buildEnvironment?.gradle?.gradleUserHome?.toPath()
                            ?: gradleUserHomeDir(issueData.projectRoot.getEelDescriptor())
    val commonGradleProperties = gradleUserHomeDir.resolve("gradle.properties")
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
    if (filePosition != null) {
      val fileName = filePosition.path?.fileName?.toString() ?: ""
      val fileTypePrefix = if (fileName.endsWith("gradle", true) || fileName.endsWith("gradle.kts", true)) "Build file " else ""
      issueDescription.appendLine("""
      * Where:
      $fileTypePrefix'${filePosition.path}' line: ${filePosition.startLine + 1}

    """.trimIndent())
    }
    issueDescription.appendLine("""
      * What went wrong:
      Out of memory. $rootCauseMessage
    """.trimIndent())

    if (quickFixDescription.isNotEmpty()) {
      issueDescription.append("\nPossible solution:\n")
      issueDescription.append(" - Check the JVM memory arguments defined for the gradle process in:\n")
      issueDescription.append(quickFixDescription)
    }
    val description = issueDescription.toString()
    val title = getMessageTitle(rootCauseMessage ?: description)

    return object : BuildIssue {
      override val title: String = title
      override val description: String = description
      override val quickFixes = quickFixes
      override fun getNavigatable(project: Project): Navigatable? {
        return filePosition?.run { FileNavigatable(project, filePosition) }
      }
    }
  }
}
