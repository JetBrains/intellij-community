// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.psi.KtFile

internal class OutdatedIntelliJPlatformGradlePluginVersionInspection : LocalInspectionTool() {

  override fun isAvailableForFile(file: PsiFile): Boolean {
    if (file is KtFile) return file.name.endsWith(".gradle.kts")
    return file.name.endsWith(".versions.toml") && file.project.getService(IntelliJPlatformVersionCatalogUpdater::class.java) != null
  }

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    val model = IntelliJPlatformGradleModelProvider.getInstance(file.project).getModel(file) ?: return null
    val currentVersion = GradleVersion.version(model.currentPluginVersion)
    val latestVersion = GradleVersion.version(model.latestPluginVersion)
    if (currentVersion >= latestVersion) return null

    val message = DevKitGradleBundle.message(
      "inspection.intellij.platform.gradle.plugin.outdated.version.problem",
      currentVersion.version,
      latestVersion.version,
    )
    return findIntelliJPlatformGradlePluginVersionReplacements(file, currentVersion.version, latestVersion.version)
      .map { (element, newContent) ->
        manager.createProblemDescriptor(
          element,
          message,
          UpdatePluginVersionQuickFix(latestVersion.version, newContent),
          ProblemHighlightType.WARNING,
          isOnTheFly,
        )
      }
      .toTypedArray()
  }
}

private class UpdatePluginVersionQuickFix(
  private val latestVersion: String,
  private val newContent: String,
) : LocalQuickFix {

  @Suppress("DialogTitleCapitalization")
  override fun getFamilyName(): @IntentionFamilyName String =
    DevKitGradleBundle.message("inspection.intellij.platform.gradle.plugin.outdated.version.quick.fix.family")

  override fun getName(): @IntentionName String =
    DevKitGradleBundle.message("inspection.intellij.platform.gradle.plugin.outdated.version.quick.fix", latestVersion)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    ElementManipulators.handleContentChange(descriptor.psiElement, newContent)
  }
}
