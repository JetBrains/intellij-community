// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class RemoveElementWithoutFormatterFix(@IntentionFamilyName private val familyName: String) : PsiUpdateModCommandQuickFix() {

  override fun getFamilyName(): String = familyName

  override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
    val textRange = element.textRange
    element.containingFile.viewProvider.document.deleteString(textRange.startOffset, textRange.endOffset)
  }
}
