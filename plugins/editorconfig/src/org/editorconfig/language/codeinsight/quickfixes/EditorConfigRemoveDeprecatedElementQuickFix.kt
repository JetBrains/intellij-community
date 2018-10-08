// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement

class EditorConfigRemoveDeprecatedElementQuickFix : LocalQuickFix {
  override fun getFamilyName() = EditorConfigBundle["quickfix.deprecated.element.remove"]
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement as? EditorConfigDescribableElement ?: return
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled(element::delete)
    ApplicationManager.getApplication().invokeLater {
      CompletionAutoPopupHandler.invokeCompletion(CompletionType.BASIC, true, project, editor, 0, false)
    }
  }
}
