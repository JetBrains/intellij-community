/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

class CutLineActionHandler extends EditorWriteActionHandler {
  private final boolean myToLineStart;
  private final boolean myIgnoreSelection;
  private final boolean myCopyToClipboard;

  CutLineActionHandler(boolean toLineStart, boolean ignoreSelection, boolean copyToClipboard) {
    myToLineStart = toLineStart;
    myIgnoreSelection = ignoreSelection;
    myCopyToClipboard = copyToClipboard;
  }

  @Override
  public void executeWriteAction(Editor editor, DataContext dataContext) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!myIgnoreSelection && selectionModel.hasSelection()) {
      delete(editor, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      return;
    }
    
    final Document doc = editor.getDocument();
    int caretOffset = editor.getCaretModel().getOffset();
    if ((myToLineStart && (caretOffset == 0)) || (!myToLineStart && (caretOffset >= doc.getTextLength()))) {
      return;
    }
    final int lineNumber = doc.getLineNumber(caretOffset);
    int lineEndOffset = doc.getLineEndOffset(lineNumber);
    int lineStartOffset = doc.getLineStartOffset(lineNumber);

    if (editor.isColumnMode() && editor.getCaretModel().supportsMultipleCarets() 
        && caretOffset == (myToLineStart ? lineStartOffset : lineEndOffset)) {
      return;
    }

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

    delete(editor, start, end);
  }

  private void delete(@NotNull Editor editor, int start, int end) {
    if (myCopyToClipboard) {
      KillRingUtil.copyToKillRing(editor, start, end, true);
    }
    else {
      CopyPasteManager.getInstance().stopKillRings();
    }
    editor.getDocument().deleteString(start, end);

    // in case the caret was in the version space, we force it to go back to the real offset
    editor.getCaretModel().moveToOffset(start);
  }
}
