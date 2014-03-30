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
 * Date: May 20, 2002
 * Time: 4:13:37 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

public class ToggleCaseAction extends TextComponentEditorAction {
  public ToggleCaseAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(final Editor editor, @Nullable Caret caret, DataContext dataContext) {
      final SelectionModel selectionModel = editor.getSelectionModel();

      if (selectionModel.hasBlockSelection()) {
        final int[] starts = selectionModel.getBlockSelectionStarts();
        final int[] ends = selectionModel.getBlockSelectionEnds();
        LogicalPosition blockStart = selectionModel.getBlockStart();
        LogicalPosition blockEnd = selectionModel.getBlockEnd();

        selectionModel.removeBlockSelection();
        selectionModel.removeSelection();

        for (int i = 0; i < starts.length; i++) {
          int startOffset = starts[i];
          int endOffset = ends[i];
          final String text = editor.getDocument().getCharsSequence().subSequence(startOffset, endOffset).toString();
          String converted = toCase(text, true);
          if (text.equals(converted)) {
            converted = toCase(text, false);
          }
          editor.getDocument().replaceString(startOffset, endOffset, converted);
        }
        if (blockStart != null && blockEnd != null) {
          selectionModel.setBlockSelection(blockStart, blockEnd);
        }
      }
      else {
        final Ref<Boolean> toLowerCase = new Ref<Boolean>(Boolean.FALSE);
        runForCaret(editor, caret, new CaretAction() {
          @Override
          public void perform(Caret caret) {
            if (!caret.hasSelection()) {
              caret.selectWordAtCaret(true);
            }
            String selectedText = caret.getSelectedText();
            if (selectedText != null && !selectedText.equals(toCase(selectedText, true))) {
              toLowerCase.set(Boolean.TRUE);
            }
          }
        });
        runForCaret(editor, caret, new CaretAction() {
          @Override
          public void perform(Caret caret) {
            VisualPosition caretPosition = caret.getVisualPosition();
            int selectionStartOffset = caret.getSelectionStart();
            int selectionEndOffset = caret.getSelectionEnd();
            VisualPosition selectionStartPosition = caret.getSelectionStartPosition();
            VisualPosition selectionEndPosition = caret.getSelectionEndPosition();
            caret.removeSelection();
            String text = editor.getDocument().getText(new TextRange(selectionStartOffset, selectionEndOffset));
            editor.getDocument().replaceString(selectionStartOffset, selectionEndOffset, toCase(text, toLowerCase.get()));
            caret.moveToVisualPosition(caretPosition);
            caret.setSelection(selectionStartPosition, selectionStartOffset, selectionEndPosition, selectionEndOffset);
          }
        });
      }
    }

    private static void runForCaret(Editor editor, Caret caret, CaretAction action) {
      if (caret == null) {
        editor.getCaretModel().runForEachCaret(action);
      }
      else {
        action.perform(caret);
      }
    }
    private static String toCase(final String text, final boolean lower) {
      StringBuilder builder = new StringBuilder(text.length());
      boolean prevIsSlash = false;
      for( int i = 0; i < text.length(); ++i) {
        char c = text.charAt(i);
        if( !prevIsSlash ) {
          c = lower ? Character.toLowerCase(c) : Character.toUpperCase(c);
        }
        prevIsSlash = c == '\\';
        builder.append(c);
      }
      return builder.toString();
    }
  }
}
