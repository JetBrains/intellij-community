// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.issue.quickfix.GradleSettingsQuickFix
import org.jetbrains.plugins.gradle.util.GradleBundle

@ApiStatus.Experimental
class IncorrectGradleJdkIssue(projectPath: String,
                              jdkHome: String,
                              message: String,
                              isResolveProjectTask: Boolean) : BuildIssue {
  override val title: String = GradleBundle.message("gradle.incorrect.jvm.issue.title")
  override val description: String
  override val quickFixes: List<BuildIssueQuickFix>
  override fun getNavigatable(project: Project): Navigatable? = null

  init {
    val gradleSettingsFix = GradleSettingsQuickFix(
      projectPath,
      isResolveProjectTask,
      GradleSettingsQuickFix.GradleJvmChangeDetector,
      GradleBundle.message("gradle.settings.text.jvm.path")
    )
    quickFixes = listOf(gradleSettingsFix)
    val fixLink = "<a href=\"${gradleSettingsFix.id}\">${GradleBundle.message("gradle.open.gradle.settings")}</a>"
    description = "$title: '$jdkHome'.\n$message\n$fixLink\n"
  }
}
