// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.events.BuildEventsNls
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.Navigatable
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.quickfix.GradleVersionQuickFix
import org.jetbrains.plugins.gradle.util.GradleBundle

abstract class AbstractGradleBuildIssue : BuildIssue {

  private val configurator = BuildIssueConfigurator()

  final override val title: @BuildEventsNls.Title String
    get() = configurator.title

  final override val quickFixes: List<BuildIssueQuickFix>
    get() = configurator.quickFixes

  final override val description: @BuildEventsNls.Description String
    get() = configurator.createDescription()

  override fun getNavigatable(project: Project): Navigatable? = null

  fun setTitle(title: @BuildEventsNls.Title String) {
    configurator.title = title
  }

  fun addDescription(description: @BuildEventsNls.Description String) {
    configurator.description.add(description)
  }

  fun addQuickFixPrompt(quickFixPrompt: @BuildEventsNls.Description String) {
    configurator.quickFixPrompts.add(quickFixPrompt)
  }

  fun addQuickFix(quickFix: BuildIssueQuickFix) {
    configurator.quickFixes.add(quickFix)
  }

  fun addGradleVersionQuickFix(projectPath: String, gradleVersion: GradleVersion) {
    val quickFix = GradleVersionQuickFix(projectPath, gradleVersion, true)
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.gradle.version.auto", quickFix.id, gradleVersion.version))
    addQuickFix(quickFix)
  }

  private class BuildIssueConfigurator {

    lateinit var title: @BuildEventsNls.Title String
    val description: MutableList<@BuildEventsNls.Description String> = ArrayList()
    val quickFixPrompts: MutableList<@BuildEventsNls.Description String> = ArrayList()
    val quickFixes: MutableList<BuildIssueQuickFix> = ArrayList()

    fun createDescription(): @NlsSafe String {
      return buildString {
        append(description.joinToString("\n\n"))
          .append("\n")
        if (quickFixPrompts.isNotEmpty()) {
          append("\n")
          append(GradleBundle.message("gradle.build.quick.fix.title", quickFixPrompts.size))
          append("\n")
          append(quickFixPrompts.joinToString("\n") { " - $it" })
          append("\n")
        }
      }
    }
  }
}