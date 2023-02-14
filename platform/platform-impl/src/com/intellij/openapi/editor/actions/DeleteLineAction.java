/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class DeleteLineAction extends TextComponentEditorAction {
  public DeleteLineAction() {
    super(new Handler());
  }

  public static class CheckHandler extends EditorWriteActionHandler {
    private final EditorWriteActionHandler myOriginal;

    public CheckHandler(EditorActionHandler original) {
      myOriginal = (EditorWriteActionHandler)original;
    }

    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (CtrlYActionChooser.isCurrentShortcutOk(dataContext)) super.doExecute(editor, caret, dataContext);
    }

    @Override
    public void executeWriteAction(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      myOriginal.executeWriteAction(editor, caret, dataContext);
    }
  }

  private static class Handler extends EditorWriteActionHandler {

    @Override
    public void executeWriteAction(final @NotNull Editor editor, Caret caret, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      CopyPasteManager.getInstance().stopKillRings();
      final Document document = editor.getDocument();

      final List<Caret> carets = caret == null ? editor.getCaretModel().getAllCarets() : Collections.singletonList(caret);

      editor.getCaretModel().runBatchCaretOperation(() -> {
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

          DocumentGuardedTextUtil.deleteString(editor.getDocument(), currentRange.getStartOffset(), currentRange.getEndOffset());

          for (int i = caretIndex + 1; i <= currentCaretIndex; i++) {
            carets.get(i).moveToVisualPosition(new VisualPosition(targetLine, caretColumns[i]));
          }
        }
      });
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  private static TextRange getRangeToDelete(Editor editor, Caret caret) {
    int selectionStart = caret.getSelectionStart();
    int selectionEnd = caret.getSelectionEnd();
    int startOffset = EditorUtil.getNotFoldedLineStartOffset(editor, selectionStart, true);
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
    if (selectionEnd > 0 && selectionEnd != selectionStart) selectionEnd--;
    int endOffset = EditorUtil.getNotFoldedLineEndOffset(editor, selectionEnd, true);
    if (endOffset < editor.getDocument().getTextLength()) {
      endOffset++;
    }
    else if (startOffset > 0) {
      startOffset--;
    }
    return new TextRange(startOffset, endOffset);
  }
}
