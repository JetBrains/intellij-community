// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.build.issue.ConfigurableBuildIssue
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.concurrent.CompletableFuture

internal class OutdatedIntelliJPlatformGradlePluginVersionIssue(
  projectPath: String,
  currentVersion: Version,
  latestVersion: Version,
) : ConfigurableBuildIssue() {

  init {
    setTitle(DevKitGradleBundle.message("intellij.platform.gradle.plugin.outdated.version.title"))
    @Suppress("DialogTitleCapitalization")
    addDescription(
      DevKitGradleBundle.message(
        "intellij.platform.gradle.plugin.outdated.version.description",
        currentVersion.toString()
      )
    )

    addIntelliJPlatformGradlePluginVersionQuickFix(projectPath, currentVersion, latestVersion)
  }

  private fun addIntelliJPlatformGradlePluginVersionQuickFix(
    projectPath: String,
    currentVersion: Version,
    latestVersion: Version,
  ) {
    val quickFix = IntelliJPlatformGradlePluginVersionQuickFix(projectPath, currentVersion.toString(), latestVersion.toString())
    val hyperlinkReference = addQuickFix(quickFix)

    @Suppress("DialogTitleCapitalization")
    addQuickFixPrompt(DevKitGradleBundle.message("intellij.platform.gradle.plugin.outdated.version.quick.fix", hyperlinkReference))
  }

  fun addOpenInspectionSettingsQuickFix(inspectionShortName: String) {
    val hyperlinkReference = addQuickFix(OpenInspectionSettingsFix(inspectionShortName))
    addQuickFixPrompt(GradleBundle.message("gradle.build.quick.fix.edit.inspection.settings", hyperlinkReference))
  }

  private class OpenInspectionSettingsFix(private val inspectionShortName: String) : BuildIssueQuickFix {
    override val id: String = "open_inspection_settings"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      val inspectionProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
      EditInspectionToolsSettingsAction.editToolSettings(project, inspectionProfile, inspectionShortName)
      return CompletableFuture.completedFuture(null)
    }
  }
}
