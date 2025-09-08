// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.fix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.PropertyKeyValueFormat
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.util.GradleUtil.getWrapperDistributionUri

/**
 * A local quick-fix implementation to replace the Gradle wrapper distribution URL in a `gradle-wrapper.properties` file
 * to match a new Gradle version. This fix targets the relevant property and applies the given version.
 *
 * @param newGradleVersion The new Gradle version to set in the `distributionUrl` property.
 */
class GradleWrapperVersionFix(private val newGradleVersion: GradleVersion) : PsiUpdateModCommandQuickFix() {
  override fun getName(): @IntentionName String {
    return GradleInspectionBundle.message("intention.name.upgrade.gradle.version", newGradleVersion.version)
  }

  override fun getFamilyName(): @IntentionFamilyName String {
    return GradleInspectionBundle.message("intention.family.name.upgrade")
  }

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val prop = element as Property
    val newGradleWrapperDistributionUri = getWrapperDistributionUri(newGradleVersion).toString().replace(":", "\\:")
    prop.setValue(newGradleWrapperDistributionUri, PropertyKeyValueFormat.FILE)
  }
}