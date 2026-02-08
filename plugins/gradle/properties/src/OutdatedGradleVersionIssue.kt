// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.properties

import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.pom.Navigatable
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiManager
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.issue.ConfigurableGradleBuildIssue
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
class OutdatedGradleVersionIssue(
    projectPath: String,
    private val currentVersion: GradleVersion,
) : ConfigurableGradleBuildIssue() {

  init {
    val latestVersion = GradleJvmSupportMatrix.getLatestMinorGradleVersion(currentVersion.majorVersion)
    setTitle(GradleBundle.message("gradle.build.issue.gradle.outdated.minor.version.title"))
    addDescription(
        GradleBundle.message(
      "gradle.build.issue.gradle.outdated.minor.version.description",
      currentVersion.version
    ))
    addDescription(
        GradleBundle.message(
      "gradle.build.issue.gradle.recommended.description",
      latestVersion.version
    ))

    addGradleVersionQuickFix(projectPath, latestVersion)
  }

  /**
   * Try to navigate to the 'gradle-wrapper.properties' file
   * and place the caret after the current Gradle version in the 'distributionUrl' property.
   */
  override fun getNavigatable(project: Project): Navigatable? {
    val basePath = project.basePath ?: return null

    return ReadAction.compute<Navigatable?, RuntimeException> {
      val wrapperPropertiesPath = GradleUtil.findDefaultWrapperPropertiesFile(Path.of(basePath)) ?: return@compute null
      val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(wrapperPropertiesPath) ?: return@compute null
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@compute null

      if (psiFile !is PropertiesFile) return@compute null

      val distributionUrlProp = psiFile.findPropertyByKey("distributionUrl") ?: return@compute null
      val versionText = currentVersion.version
      val psiElement = distributionUrlProp.psiElement
      val indexInProperty = psiElement.text.indexOf(versionText)

      if (indexInProperty < 0) return@compute null

      val indexInFile = psiElement.startOffsetInParent + indexInProperty + versionText.length
        OpenFileDescriptor(project, virtualFile, indexInFile)
    }
  }

  fun addOpenInspectionSettingsQuickFix(inspectionShortName: String) {
    val hyperlinkReference = addQuickFix(OpenInspectionSettingsFix(inspectionShortName))
    addQuickFixPrompt(
        GradleBundle.message(
      "gradle.build.quick.fix.edit.inspection.settings",
      hyperlinkReference
    ))
  }

  private class OpenInspectionSettingsFix(private val inspectionShortName: String) : BuildIssueQuickFix {
    override val id = "open_inspection_settings"

    override fun runQuickFix(project: Project, dataContext: DataContext): CompletableFuture<*> {
      val projectProfileManager = InspectionProjectProfileManager.getInstance(project)
      val inspectionProfile = projectProfileManager.getCurrentProfile()
      EditInspectionToolsSettingsAction.editToolSettings(project, inspectionProfile, inspectionShortName)
      return CompletableFuture.completedFuture(null)
    }
  }
}