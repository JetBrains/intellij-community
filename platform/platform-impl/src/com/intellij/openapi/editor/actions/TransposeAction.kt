// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.util.DocumentUtil

class TransposeAction : EditorAction(TransposeHandler()) {
  private class TransposeHandler : EditorWriteActionHandler() {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      if (isSuitableForTranspose(editor)) {
        performTranspose(editor)
      } else if (isSuitableForRotateSelections(editor)) {
        performRotateSelections(editor)
      }
    }

    private fun isSuitableForTranspose(editor: Editor): Boolean {
      return editor.caretModel.allCarets.all { !it.hasSelection() }
    }

    private fun performTranspose(editor: Editor) {
      for (caret in editor.caretModel.allCarets) {
        if (caret.offset == 0) continue
        val line = caret.logicalPosition.line
        val document = editor.document
        if (caret.offset < document.getLineEndOffset(line)) {
          val offsetBeforeCaret = DocumentUtil.getPreviousCodePointOffset(document, caret.offset)
          val offsetAfterCaret = DocumentUtil.getNextCodePointOffset(document, caret.offset)
          if (offsetBeforeCaret >= 0) {
            val codepointBeforeCaret = document.charsSequence.subSequence(offsetBeforeCaret, caret.offset)
            val codepointAfterCaret = document.charsSequence.subSequence(caret.offset, offsetAfterCaret)
            document.replaceString(offsetBeforeCaret, offsetAfterCaret, "$codepointAfterCaret$codepointBeforeCaret")
            caret.moveToOffset(offsetAfterCaret)
          }
        }
        else {
          // when the caret is at EOL, swap two last characters of the line and don't move caret
          val offsetBeforeCaret = DocumentUtil.getPreviousCodePointOffset(document, caret.offset)
          val offset2BeforeCaret = DocumentUtil.getPreviousCodePointOffset(document, offsetBeforeCaret)
          if (offset2BeforeCaret >= 0) {
            val codepoint2BeforeCaret = document.charsSequence.subSequence(offset2BeforeCaret, offsetBeforeCaret)
            val codepointBeforeCaret = document.charsSequence.subSequence(offsetBeforeCaret, caret.offset)
            document.replaceString(offset2BeforeCaret, caret.offset, "$codepointBeforeCaret$codepoint2BeforeCaret")
          }
        }
      }
    }

    private fun isSuitableForRotateSelections(editor: Editor): Boolean {
      return editor.caretModel.allCarets.all { it.hasSelection() }
    }

    private fun performRotateSelections(editor: Editor) {
      if (editor.caretModel.caretCount <= 1) return
      val isBackwards = false // we are rotating carets forward, but if you want to change it, you can do it here

      val carets = editor.caretModel.allCarets
        .sortedByDescending { it.selectionRange.endOffset }

      val textToReplace = carets
        .map { editor.document.getText(it.selectionRange) }
        .rotateElements(!isBackwards) // isBackwards is inversed because carets are in the reverse order (sorted by descending offset)

      assert(carets.size == textToReplace.size)
      for ((i, caret) in carets.withIndex()) {
        val selection = caret.selectionRange
        val newText = textToReplace[i]
        editor.document.replaceString(selection.startOffset, selection.endOffset, newText)
        caret.setSelection(selection.startOffset, selection.startOffset + newText.length)
        caret.moveToOffset(caret.selectionEnd)
      }
    }

    private fun <T> List<T>.rotateElements(isBackwards: Boolean): List<T> {
      if (this.size <= 1) return this.toList()
      return if (isBackwards) {
        this.subList(1, this.size) + this[0]
      } else {
        listOf(this[this.size - 1]) + this.subList(0, this.size - 1)
      }
    }
  }
}
