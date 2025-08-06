// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.PropertyKeyValueFormat
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.util.GradleUtil.getWrapperDistributionUri

class GradleLatestMinorVersionInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    // TODO not sure if this should be limited to "gradle-wrapper.properties" file
    if (holder.file.name != "gradle-wrapper.properties") return PsiElementVisitor.EMPTY_VISITOR

    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element !is Property) return
        if (element.key != "distributionUrl") return

        // extract the current Gradle version from the wrapper properties file
        val regex = "gradle-(.+)-bin\\.zip$".toRegex()
        val group = regex.find(element.text)?.groups[1] ?: return
        val currentVersion = group.value
        val versionTextRange = TextRange(group.range.first, group.range.last + 1)
        val currentGradleVersion = try {
          GradleVersion.version(currentVersion)
        }
        catch (_: Throwable) {
          return
        }

        val newestMinorGradleVersion = GradleJvmSupportMatrix.getNewestMinorGradleVersion(currentGradleVersion.majorVersion)
        if (currentGradleVersion >= newestMinorGradleVersion) return

        val localFix = object : LocalQuickFixOnPsiElement(element) {
          override fun getText(): @IntentionName String {
            return "Upgrade to Gradle ${newestMinorGradleVersion.version}"
          }

          override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val uri = getWrapperDistributionUri(newestMinorGradleVersion)
            val prop = startElement as Property
            prop.setValue(uri.toString(), PropertyKeyValueFormat.FILE)
          }

          override fun getFamilyName(): @IntentionFamilyName String {
            return "Gradle"
          }

          override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
            return IntentionPreviewInfo.CustomDiff(
              previewDescriptor.psiElement.containingFile.fileType,
              previewDescriptor.startElement.text,
              "distributionUrl=" + getWrapperDistributionUri(newestMinorGradleVersion).toString()
            )
          }
        }

        holder.registerProblem(
          element,
          "Outdated Gradle minor version",
          ProblemHighlightType.WARNING,
          versionTextRange,
          localFix
        )
      }
    }
  }

}