// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.concurrent.CompletableFuture

internal class NoJdkForToolingProxyBuildIssue : BuildIssue {

  override val title: String = GradleBundle.message("gradle.no.jdk.for.tooling.proxy.issue.title")
  override val description: String
  override val quickFixes: List<BuildIssueQuickFix>
  override fun getNavigatable(project: Project): Navigatable? = null

  class QuickFix : BuildIssueQuickFix {
    override val id: String = "gradle.no.jdk.for.tooling.proxy"
    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      ProjectSettingsService.getInstance(project)
        .openProjectSettings()
      return CompletableFuture.completedFuture(null)
    }
  }

  init {
    val fix = QuickFix()
    quickFixes = listOf(fix)
    val fixLink = "<a href=\"${fix.id}\">${GradleBundle.message("gradle.no.jdk.for.tooling.proxy.issue.action")}</a>"
    val message = GradleBundle.message("gradle.no.jdk.for.tooling.proxy.issue.description")
    description = "$message: $fixLink\n"
  }
}
