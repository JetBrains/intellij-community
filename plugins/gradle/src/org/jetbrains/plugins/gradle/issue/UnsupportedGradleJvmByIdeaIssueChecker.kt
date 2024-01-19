// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.util.GradleBundle

class UnsupportedGradleJvmByIdeaIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    if (issueData.error is GradleExecutionHelper.UnsupportedGradleJvmByIdeaException) {
      val gradleVersion = issueData.error.gradleVersion
      return UnsupportedGradleJvmBuildIssue(gradleVersion, issueData.projectPath)
    }
    else if (issueData.buildEnvironment != null) {
      val javaHome = issueData.buildEnvironment.java.javaHome
      if (Util.isJavaHomeUnsupportedByIdea(javaHome.path)) {
        val gradleVersionString = issueData.buildEnvironment.gradle.gradleVersion
        val gradleVersion = GradleVersion.version(gradleVersionString)
        return UnsupportedGradleJvmBuildIssue(gradleVersion, issueData.projectPath)
      }
    }
    return null
  }

  object Util {

    /**
     * Checks that Java which provided by [javaHome] is not supported by IDEA Gradle integration.
     * @return false if the java version supported or undefined, otherwise true.
     */
    @JvmStatic
    fun isJavaHomeUnsupportedByIdea(javaHome: String): Boolean {
      val javaSdkType = ExternalSystemJdkUtil.getJavaSdkType()
      val javaVersionString = javaSdkType.getVersionString(javaHome) ?: return false
      val javaVersion = JavaVersion.tryParse(javaVersionString) ?: return false
      return !GradleJvmSupportMatrix.isJavaSupportedByIdea(javaVersion)
    }
  }
}

private class UnsupportedGradleJvmBuildIssue(
  gradleVersion: GradleVersion,
  projectPath: String
) : AbstractGradleBuildIssue() {
  init {
    val isAndroidStudio = "AndroidStudio" == PlatformUtils.getPlatformPrefix()
    val oldestSupportedJavaVersion = GradleJvmSupportMatrix.getOldestSupportedJavaVersionByIdea()
    val oldestCompatibleJavaVersion = GradleJvmSupportMatrix.suggestOldestSupportedJavaVersion(gradleVersion)

    setTitle(GradleBundle.message("gradle.build.issue.gradle.jvm.unsupported.title"))
    addDescription(GradleBundle.message("gradle.build.issue.gradle.jvm.unsupported.header"))
    addDescription(GradleBundle.message(
      "gradle.build.issue.gradle.jvm.unsupported.description",
      ApplicationNamesInfo.getInstance().fullProductName,
      oldestSupportedJavaVersion.feature
    ))
    if (!isAndroidStudio && oldestCompatibleJavaVersion != null) {
      val gradleSettingsQuickFix = GradleSettingsQuickFix(
        projectPath, true,
        GradleSettingsQuickFix.GradleJvmChangeDetector,
        GradleBundle.message("gradle.settings.text.jvm.path")
      )
      addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.gradle.jvm", gradleSettingsQuickFix.id, oldestCompatibleJavaVersion))
      addQuickFix(gradleSettingsQuickFix)
    }
  }
}