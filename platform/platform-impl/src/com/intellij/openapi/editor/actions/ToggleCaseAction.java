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
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static com.intellij.psi.StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN;

public class ToggleCaseAction extends TextComponentEditorAction {
  public ToggleCaseAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(final @NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      final Ref<Boolean> toLowerCase = new Ref<>(Boolean.FALSE);
      runForCaret(editor, caret, c -> {
        if (!c.hasSelection()) {
          c.selectWordAtCaret(true);
        }
        int selectionStartOffset = c.getSelectionStart();
        int selectionEndOffset = c.getSelectionEnd();
        String originalText = editor.getDocument().getText(new TextRange(selectionStartOffset, selectionEndOffset));
        if (!originalText.equals(toCase(editor, selectionStartOffset, selectionEndOffset, true))) {
          toLowerCase.set(Boolean.TRUE);
        }
      });
      runForCaret(editor, caret, c -> {
        VisualPosition caretPosition = c.getVisualPosition();
        int selectionStartOffset = c.getSelectionStart();
        int selectionEndOffset = c.getSelectionEnd();
        String originalText = editor.getDocument().getText(new TextRange(selectionStartOffset, selectionEndOffset));
        String result = toCase(editor, selectionStartOffset, selectionEndOffset, toLowerCase.get());
        editor.getDocument().replaceString(selectionStartOffset, selectionEndOffset,
                                           result);
        c.moveToVisualPosition(caretPosition);
        //Restore selection for TextComponentEditorImpl/TextAreaDocument etc.
        if (!c.hasSelection()) {
          c.setSelection(selectionStartOffset, selectionEndOffset + result.length() - originalText.length());
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
      EditorHighlighter highlighter = editor.getHighlighter();
      HighlighterIterator iterator = highlighter.createIterator(startOffset);
      StringBuilder builder = new StringBuilder(endOffset - startOffset);
      while (!iterator.atEnd()) {
        int start = MathUtil.clamp(iterator.getStart(), startOffset, endOffset);
        int end = MathUtil.clamp(iterator.getEnd(), startOffset, endOffset);
        CharSequence fragment = text.subSequence(start, end);

        builder.append(iterator.getTokenType() == VALID_STRING_ESCAPE_TOKEN ? fragment :
                       lower ? fragment.toString().toLowerCase(Locale.getDefault()) :
                       fragment.toString().toUpperCase(Locale.getDefault()));

        if (end == endOffset) break;
        iterator.advance();
      }
      return builder.toString();
    }
  }
}
