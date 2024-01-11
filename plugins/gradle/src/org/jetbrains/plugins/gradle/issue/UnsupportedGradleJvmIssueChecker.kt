// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.pom.Navigatable
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.UnsupportedGradleJvmIssueChecker.Util.isJavaHomeUnsupportedByIdea
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.util.GradleBundle

class UnsupportedGradleJvmIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    val gradleVersion: GradleVersion
    if (issueData.error is GradleExecutionHelper.UnsupportedGradleJvmByIdeaException) {
      gradleVersion = issueData.error.gradleVersion
    }
    else if (issueData.buildEnvironment != null) {
      val javaHome = issueData.buildEnvironment.java.javaHome
      if (!isJavaHomeUnsupportedByIdea(javaHome.path)) {
        return null
      }
      val gradleVersionString = issueData.buildEnvironment.gradle.gradleVersion
      gradleVersion = GradleVersion.version(gradleVersionString)
    }
    else {
      return null
    }

    val title = GradleBundle.message("gradle.build.issue.gradle.jvm.unsupported.title")
    val description = DescriptionBuilder()
    val oldestSupportedJavaVersion = GradleJvmSupportMatrix.getOldestSupportedJavaVersionByIdea()
    description.addDescription(
      GradleBundle.message(
        "gradle.build.issue.gradle.jvm.unsupported.description",
        ApplicationNamesInfo.getInstance().fullProductName,
        oldestSupportedJavaVersion.feature
      )
    )
    val gradleSettingsFix = GradleSettingsQuickFix(
      issueData.projectPath, true,
      GradleSettingsQuickFix.GradleJvmChangeDetector,
      GradleBundle.message("gradle.settings.text.jvm.path")
    )
    val isAndroidStudio = "AndroidStudio" == PlatformUtils.getPlatformPrefix()
    val oldestCompatibleJavaVersion = GradleJvmSupportMatrix.suggestOldestSupportedJavaVersion(gradleVersion)
    if (!isAndroidStudio && oldestCompatibleJavaVersion != null) {
      description.addQuickFixPrompt(
        GradleBundle.message("gradle.build.quick.fix.gradle.jvm", oldestCompatibleJavaVersion, gradleSettingsFix.id)
      )
    }
    return object : BuildIssue {
      override val title = title
      override val description = description.toString()
      override val quickFixes = listOf(gradleSettingsFix)
      override fun getNavigatable(project: Project): Navigatable? = null
    }
  }

  object Util {

    /**
     * Checks that Java which provided by [javaHome] is not supported by IDEA Gradle integration.
     * @return false if java version supported or undefined, otherwise true.
     */
    @JvmStatic
    fun isJavaHomeUnsupportedByIdea(javaHome: String): Boolean {
      val javaSdkType = ExternalSystemJdkUtil.getJavaSdkType()
      val javaVersionString = javaSdkType.getVersionString(javaHome) ?: return false
      val javaVersion = JavaVersion.tryParse(javaVersionString) ?: return false
      return !GradleJvmSupportMatrix.isJavaSupportedByIdea(javaVersion)
    }
  }

  private class DescriptionBuilder {

    private val descriptions = ArrayList<String>()
    private val quickFixPrompts = ArrayList<String>()

    fun addDescription(description: @NlsContexts.DetailedDescription String) {
      descriptions.add(description)
    }

    fun addQuickFixPrompt(prompt: @NlsContexts.DetailedDescription String) {
      quickFixPrompts.add(prompt)
    }

    override fun toString(): String {
      return buildString {
        append(descriptions.joinToString("\n"))
          .append("\n")
        if (quickFixPrompts.isNotEmpty()) {
          append(GradleBundle.message("gradle.build.quick.fix.title"))
            .append("\n")
          append(quickFixPrompts.joinToString("\n") { "- $it" })
            .append("\n")
        }
      }
    }
  }
}