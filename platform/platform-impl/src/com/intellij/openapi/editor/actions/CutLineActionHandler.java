/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class CutLineActionHandler extends EditorWriteActionHandler {
  private final boolean myToLineStart;

  CutLineActionHandler(boolean toLineStart) {
    super(true);
    myToLineStart = toLineStart;
  }

  @Override
  public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (caret == null) {
      caret = editor.getCaretModel().getCurrentCaret();
    }
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
      start = lineStartOffset;
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
