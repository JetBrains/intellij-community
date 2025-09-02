// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editorconfig.frontend.editor

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.editorconfig.common.syntax.psi.EditorConfigEnumerationPattern
import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeader
import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.impl.TypedActionImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil.getParentOfType

class EditorConfigTypedHandlerDelegate : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (file !is EditorConfigPsiFile) return Result.CONTINUE
    if (charTyped != '=') return Result.CONTINUE

    AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
    return Result.CONTINUE
  }

  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (file !is EditorConfigPsiFile) return Result.CONTINUE
    if (c != ',') return Result.CONTINUE

    val caretModel = editor.caretModel
    val offset = caretModel.offset

    if (offset == 0) return Result.CONTINUE

    val psiBeforeCaret = file.findElementAt(offset - 1) ?: return Result.CONTINUE
    val header = findEnclosableHeader(psiBeforeCaret) ?: return Result.CONTINUE
    val lBraceIndex = header.textRange.startOffset + 1
    val rBraceIndex = header.textRange.endOffset + 1
    val newOffset = offset + 1

    val document = editor.document
    val typedAction = TypedAction.getInstance() as TypedActionImpl

    runWriteAction {
      typedAction.defaultRawTypedHandler.beginUndoablePostProcessing()
      document.insertString(lBraceIndex, "{")
      document.insertString(rBraceIndex, "}")
      caretModel.moveToOffset(newOffset)
      editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
      editor.selectionModel.removeSelection()
    }

    return Result.CONTINUE
  }

  private fun findEnclosableHeader(element: PsiElement): EditorConfigHeader? {
    return getParentOfType(
      element, EditorConfigHeader::class.java, false,
      EditorConfigEnumerationPattern::class.java,
    )
  }
}
