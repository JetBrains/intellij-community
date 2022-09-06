// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.util.DocumentUtil

class TransposeAction : EditorAction(TransposeHandler()) {
  private class TransposeHandler : EditorWriteActionHandler.ForEachCaret() {
    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
      return !caret.hasSelection() && caret.offset > 0
    }

    override fun executeWriteAction(editor: Editor, caret: Caret, dataContext: DataContext?) {
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
}
