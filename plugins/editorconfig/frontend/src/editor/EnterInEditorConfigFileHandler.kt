// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.editorconfig.frontend.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.editorconfig.common.syntax.psi.EditorConfigElementTypes
import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

// Those functions look somewhat C-ish...
internal class EnterInEditorConfigFileHandler : EnterHandlerDelegate {

  override fun preprocessEnter(
    file: PsiFile,
    editor: Editor,
    caretOffsetRef: Ref<Int>,
    caretAdvance: Ref<Int>,
    dataContext: DataContext,
    originalHandler: EditorActionHandler?
  ): Result {
    if (file !is EditorConfigPsiFile) return Result.Continue

    val document = editor.document
    val project = file.project
    val documentManager = PsiDocumentManager.getInstance(project)

    documentManager.commitDocument(document)

    val caretOffset = caretOffsetRef.get()
    val psiUnderCaret = file.findElementAt(caretOffset)

    val elementType = psiUnderCaret?.node?.elementType
    val psiElementStart = psiUnderCaret?.text?.firstOrNull()
    val charUnderCaret = document.text.elementAtOrNull(caretOffset)

    if (elementType != EditorConfigElementTypes.LINE_COMMENT || isCommentStart(charUnderCaret)) return Result.Continue

    val (text, offset) = if (isWhitespace(charUnderCaret)) "\n$psiElementStart" to 3 else "\n$psiElementStart " to 3

    document.insertString(caretOffset, text)
    editor.caretModel.moveToOffset(caretOffset + offset)
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    editor.selectionModel.removeSelection()
    return Result.Stop
  }

  private val WHITE_SPACE = "\\s".toRegex()
  private fun isWhitespace(char: Char?) = char != null && WHITE_SPACE.matches(char.toString())
  private fun isCommentStart(char: Char?) = char != null && "#;".contains(char)
}
