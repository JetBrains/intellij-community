// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.codeinsight.inspections.findSuspiciousSpaces
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigHeader

class EditorConfigRemoveSpacesQuickFix : LocalQuickFix {
  override fun getFamilyName() = EditorConfigBundle["quickfix.header.remove.spaces.description"]
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val header = descriptor.psiElement as? EditorConfigHeader ?: return
    val manager = CodeStyleManager.getInstance(project)
    val spaces = findSuspiciousSpaces(header)
    if (spaces.isEmpty()) return
    manager.performActionWithFormatterDisabled { spaces.forEach { it.delete() } }
  }
}
