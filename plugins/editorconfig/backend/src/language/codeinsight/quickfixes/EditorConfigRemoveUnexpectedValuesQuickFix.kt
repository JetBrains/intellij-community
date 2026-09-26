// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueIdentifier
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOptionValueList
import com.intellij.editorconfig.common.syntax.psi.impl.EditorConfigElementFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.annotations.Nls
import kotlin.math.abs
import kotlin.math.min

class EditorConfigRemoveUnexpectedValuesQuickFix : LocalQuickFix {
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.value.list.remove.others")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val list = descriptor.psiElement as EditorConfigOptionValueList
    val identifier = findClosestListElement(list) ?: return
    val factory = EditorConfigElementFactory.getInstance(project)
    val replacement = factory.createValueIdentifier(identifier.text)
    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled { list.replace(replacement) }
  }

  private fun findClosestListElement(list: EditorConfigOptionValueList): EditorConfigOptionValueIdentifier? {
    val editor = FileEditorManager.getInstance(list.project).selectedTextEditor ?: return null
    return list.optionValueIdentifierList.minByOrNull { element: PsiElement ->
      min(abs(element.textRange.startOffset - editor.caretModel.offset),
          abs(element.textRange.endOffset - editor.caretModel.offset))
    }
  }
}
