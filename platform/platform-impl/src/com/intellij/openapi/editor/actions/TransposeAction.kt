// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler

class TransposeAction : EditorAction(TransposeHandler()) {
  private class TransposeHandler : EditorWriteActionHandler(true) {
    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
      return !caret.hasSelection() && caret.offset > 0
    }

    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      val caret = caret ?: editor.caretModel.currentCaret
      val line = caret.logicalPosition.line
      if (caret.offset < editor.document.getLineEndOffset(line) - editor.document.getLineSeparatorLength(line)) {
        val characterBeforeCaret = editor.document.charsSequence[caret.offset - 1]
        val characterAfterCaret = editor.document.charsSequence[caret.offset]
        editor.document.replaceString(caret.offset - 1, caret.offset + 1, "$characterAfterCaret$characterBeforeCaret")
        caret.moveToOffset(caret.offset + 1)
      }
      else if (caret.offset >= 2) {
        // when the caret is at EOL, swap two last characters of the line and don't move caret
        val characterBeforeCaret = editor.document.charsSequence[caret.offset - 2]
        val characterAfterCaret = editor.document.charsSequence[caret.offset - 1]
        editor.document.replaceString(caret.offset - 2, caret.offset, "$characterAfterCaret$characterBeforeCaret")
      }
    }


  }
}
