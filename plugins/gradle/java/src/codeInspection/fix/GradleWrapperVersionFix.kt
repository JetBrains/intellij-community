// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.fix

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.PropertyKeyValueFormat
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.DISTRIBUTION_URL_KEY
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.util.GradleUtil.getWrapperDistributionUri

/**
 * A local quick-fix implementation to replace the Gradle wrapper distribution URL in a `gradle-wrapper.properties` file
 * to match a new Gradle version. This fix targets the relevant property and applies the given version.
 *
 * @constructor Initializes the quick-fix with the corresponding PSI element and the target Gradle version.
 *
 * @param element The PSI element representing the `distributionUrl` property in `gradle-wrapper.properties` to update.
 * @param newGradleVersion The new Gradle version to set in the `distributionUrl` property.
 */
class GradleWrapperVersionFix(element: PsiElement, private val newGradleVersion: GradleVersion) : LocalQuickFixOnPsiElement(element) {
  private val newGradleWrapperDistributionUri = getWrapperDistributionUri(newGradleVersion).toString().replace(":", "\\:")

  override fun getText(): @IntentionName String {
    return GradleInspectionBundle.message("intention.name.upgrade.gradle.version", newGradleVersion.version)
  }

  override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val prop = startElement as Property
    prop.setValue(newGradleWrapperDistributionUri, PropertyKeyValueFormat.FILE)
  }

  override fun getFamilyName(): @IntentionFamilyName String {
    return GradleInspectionBundle.message("intention.family.name.upgrade")
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
    return IntentionPreviewInfo.CustomDiff(
      previewDescriptor.psiElement.containingFile.fileType,
      previewDescriptor.startElement.text,
      "$DISTRIBUTION_URL_KEY=$newGradleWrapperDistributionUri"
    )
  }
}