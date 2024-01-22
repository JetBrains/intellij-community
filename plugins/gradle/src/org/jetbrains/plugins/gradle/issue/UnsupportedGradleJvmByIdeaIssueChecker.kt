// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.util.GradleBundle

class UnsupportedGradleJvmByIdeaIssueChecker : GradleIssueChecker {

  override fun check(issueData: GradleIssueData): BuildIssue? {
    if (issueData.error is GradleExecutionHelper.UnsupportedGradleJvmByIdeaException) {
      return UnsupportedGradleJvmBuildIssue(issueData.error.gradleVersion, issueData.error.javaVersion, issueData.projectPath)
    }
    return null
  }
}

private class UnsupportedGradleJvmBuildIssue(
  gradleVersion: GradleVersion,
  javaVersion: JavaVersion?,
  projectPath: String
) : AbstractGradleBuildIssue() {
  init {
    val oldestSupportedJavaVersionByIdea = GradleJvmSupportMatrix.getOldestSupportedJavaVersionByIdea()
    val oldestSupportedJavaVersionByGradle = GradleJvmSupportMatrix.suggestOldestSupportedJavaVersion(gradleVersion)
    val oldestSupportedJavaVersion = oldestSupportedJavaVersionByGradle ?: oldestSupportedJavaVersionByIdea
    setTitle(GradleBundle.message("gradle.build.issue.gradle.jvm.unsupported.title"))
    if (javaVersion == null) {
      addDescription(GradleBundle.message("gradle.build.issue.gradle.jvm.unsupported.unknown.description"))
    }
    else {
      addDescription(GradleBundle.message("gradle.build.issue.gradle.jvm.unsupported.description", javaVersion))
    }
    addDescription(GradleBundle.message("gradle.build.issue.gradle.jvm.supported.description", oldestSupportedJavaVersion))
    addGradleJvmQuickFix(projectPath, oldestSupportedJavaVersion)
  }
}