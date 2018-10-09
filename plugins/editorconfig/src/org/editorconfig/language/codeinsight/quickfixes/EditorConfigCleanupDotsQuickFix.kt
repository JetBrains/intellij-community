// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigElementTypes
import org.editorconfig.language.psi.EditorConfigQualifiedOptionKey

class EditorConfigCleanupDotsQuickFix : LocalQuickFix {
  override fun getFamilyName() = EditorConfigBundle["quickfix.dots.cleanup.description"]
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val key = descriptor.psiElement as? EditorConfigQualifiedOptionKey ?: return
    val manager = CodeStyleManager.getInstance(project)
    SyntaxTraverser
      .psiTraverser()
      .children(key)
      .filter { it !is PsiWhiteSpace }
      .zipWithNext()
      .forEach { (previous, current) ->
        if (previous?.node?.elementType == EditorConfigElementTypes.DOT
            && current?.node?.elementType == EditorConfigElementTypes.DOT) {
          manager.performActionWithFormatterDisabled(current::delete)
        }
      }
  }
}
