// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

final class CutLineActionHandler extends EditorWriteActionHandler.ForEachCaret {
  private final boolean myToLineStart;

  CutLineActionHandler(boolean toLineStart) {
    myToLineStart = toLineStart;
  }

  @Override
  public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (caret.hasSelection()) {
      delete(editor, caret, caret.getSelectionStart(), caret.getSelectionEnd());
      return;
    }
    
    final Document doc = editor.getDocument();
    int caretOffset = caret.getOffset();
    if ((myToLineStart && (caretOffset == 0)) || (!myToLineStart && (caretOffset >= doc.getTextLength()))) {
      return;
    }
    final int lineNumber = doc.getLineNumber(caretOffset);
    int lineEndOffset = doc.getLineEndOffset(lineNumber);
    int lineStartOffset = doc.getLineStartOffset(lineNumber);

    int start;
    int end;
    if (myToLineStart) {
      start = caret.getLogicalPosition().column == 0 ? lineStartOffset - 1 : lineStartOffset;
      end = caretOffset;
    }
    else {
      if (caretOffset >= lineEndOffset) {
        start = lineEndOffset;
        end = lineEndOffset + 1;
      }
      else {
        start = caretOffset;
        end = lineEndOffset;
        if (lineEndOffset < doc.getTextLength() && CharArrayUtil.isEmptyOrSpaces(doc.getCharsSequence(), caretOffset, lineEndOffset)) {
          end++;
        }
      }
    }

    delete(editor, caret, start, end);
  }

  private static void delete(@NotNull Editor editor, @NotNull Caret caret, int start, int end) {
    CopyPasteManager.getInstance().stopKillRings();
    editor.getDocument().deleteString(start, end);

    // in case the caret was in the virtual space, we force it to go back to the real offset
    caret.moveToOffset(start);
  }
}
