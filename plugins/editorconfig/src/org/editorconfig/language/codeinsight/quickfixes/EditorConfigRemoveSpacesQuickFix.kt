// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader
import org.editorconfig.language.services.EditorConfigElementFactory

class EditorConfigRemoveSpacesQuickFix(private val spaces: List<PsiWhiteSpace>) : LocalQuickFix {
  override fun getFamilyName() = EditorConfigBundle.get("quickfix.header.remove.spaces.description")
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val header = descriptor.psiElement as? EditorConfigHeader ?: return
    val manager = CodeStyleManager.getInstance(project)
    val factory = EditorConfigElementFactory.getInstance(project)
    manager.performActionWithFormatterDisabled { spaces.forEach(PsiWhiteSpace::delete) }
    val newHeaderText = header.text
    val newHeader = factory.createHeader(newHeaderText)
    manager.performActionWithFormatterDisabled { header.replace(newHeader) }
  }
}
