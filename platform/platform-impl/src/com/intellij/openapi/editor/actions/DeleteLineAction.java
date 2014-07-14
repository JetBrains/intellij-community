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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 14, 2002
 * Time: 7:18:30 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.TextRange;

import java.util.Collections;
import java.util.List;

public class DeleteLineAction extends TextComponentEditorAction {
  public DeleteLineAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(final Editor editor, Caret caret, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      CopyPasteManager.getInstance().stopKillRings();
      final Document document = editor.getDocument();

      final List<Caret> carets = caret == null ? editor.getCaretModel().getAllCarets() : Collections.singletonList(caret);

      editor.getCaretModel().runBatchCaretOperation(new Runnable() {
        @Override
        public void run() {
          int[] caretColumns = new int[carets.size()];
          int caretIndex = carets.size() - 1;
          TextRange range = getRangeToDelete(editor, carets.get(caretIndex));

          while (caretIndex >= 0) {
            int currentCaretIndex = caretIndex;
            TextRange currentRange = range;
            // find carets with overlapping line ranges
            while (--caretIndex >= 0) {
              range = getRangeToDelete(editor, carets.get(caretIndex));
              if (range.getEndOffset() < currentRange.getStartOffset()) {
                break;
              }
              currentRange = new TextRange(range.getStartOffset(), currentRange.getEndOffset());
            }

            for (int i = caretIndex + 1; i <= currentCaretIndex; i++) {
              caretColumns[i] = carets.get(i).getVisualPosition().column;
            }
            int targetLine = editor.offsetToVisualPosition(currentRange.getStartOffset()).line;

            document.deleteString(currentRange.getStartOffset(), currentRange.getEndOffset());

            for (int i = caretIndex + 1; i <= currentCaretIndex; i++) {
              carets.get(i).moveToVisualPosition(new VisualPosition(targetLine, caretColumns[i]));
            }
          }
        }
      });
    }
  }

  private static TextRange getRangeToDelete(Editor editor, Caret caret) {
    int selectionStart = caret.getSelectionStart();
    int selectionEnd = caret.getSelectionEnd();
    int startOffset = EditorUtil.getNotFoldedLineStartOffset(editor, selectionStart);
    // There is a possible case that selection ends at the line start, i.e. something like below ([...] denotes selected text,
    // '|' is a line start):
    //   |line 1
    //   |[line 2
    //   |]line 3
    // We don't want to delete line 3 here. However, the situation below is different:
    //   |line 1
    //   |[line 2
    //   |line] 3
    // Line 3 must be removed here.
    int endOffset = EditorUtil.getNotFoldedLineEndOffset(editor, selectionEnd > 0 && selectionEnd != selectionStart ? selectionEnd - 1 : selectionEnd);
    if (endOffset < editor.getDocument().getTextLength()) {
      endOffset++;
    }
    else if (startOffset > 0) {
      startOffset--;
    }
    return new TextRange(startOffset, endOffset);
  }
}
