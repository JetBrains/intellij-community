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
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static com.intellij.psi.StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;

public class ToggleCaseAction extends TextComponentEditorAction {
  public ToggleCaseAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(final Editor editor, @Nullable Caret caret, DataContext dataContext) {
      final Ref<Boolean> toLowerCase = new Ref<>(Boolean.FALSE);
      runForCaret(editor, caret, new CaretAction() {
        @Override
        public void perform(Caret caret) {
          if (!caret.hasSelection()) {
            caret.selectWordAtCaret(true);
          }
          int selectionStartOffset = caret.getSelectionStart();
          int selectionEndOffset = caret.getSelectionEnd();
          String originalText = editor.getDocument().getText(new TextRange(selectionStartOffset, selectionEndOffset));
          if (!originalText.equals(toCase(editor, selectionStartOffset, selectionEndOffset, true))) {
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
          editor.getDocument().replaceString(selectionStartOffset, selectionEndOffset,
                                             toCase(editor, selectionStartOffset, selectionEndOffset, toLowerCase.get()));
          caret.moveToVisualPosition(caretPosition);
          caret.setSelection(selectionStartPosition, selectionStartOffset, selectionEndPosition, selectionEndOffset);
        }
      });
    }

    private static void runForCaret(Editor editor, Caret caret, CaretAction action) {
      if (caret == null) {
        editor.getCaretModel().runForEachCaret(action);
      }
      else {
        action.perform(caret);
      }
    }

    private static String toCase(Editor editor, int startOffset, int endOffset, final boolean lower) {
      CharSequence text = editor.getDocument().getImmutableCharSequence();
      EditorHighlighter highlighter;
      if (editor instanceof EditorEx) {
        highlighter = ((EditorEx)editor).getHighlighter();
      }
      else {
        highlighter = new EmptyEditorHighlighter(null);
        highlighter.setText(text);
      }
      HighlighterIterator iterator = highlighter.createIterator(startOffset);
      StringBuilder builder = new StringBuilder(endOffset - startOffset);
      while (!iterator.atEnd()) {
        int start = trim(iterator.getStart(), startOffset, endOffset);
        int end = trim(iterator.getEnd(), startOffset, endOffset);
        CharSequence fragment = text.subSequence(start, end);

        builder.append(iterator.getTokenType() == VALID_STRING_ESCAPE_TOKEN ? fragment :
                       lower ? fragment.toString().toLowerCase(Locale.getDefault()) :
                       fragment.toString().toUpperCase(Locale.getDefault()));

        if (end == endOffset) break;
        iterator.advance();
      }
      return builder.toString();
    }

    private static int trim(int value, int lowerLimit, int upperLimit) {
      return Math.min(upperLimit, Math.max(lowerLimit, value));
    }
  }
}
