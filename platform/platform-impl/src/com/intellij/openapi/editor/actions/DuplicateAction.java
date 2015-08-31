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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DuplicateAction extends EditorAction {
  public DuplicateAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    public Handler() {
      super(true);
    }

    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
      duplicateLineOrSelectedBlockAtCaret(editor);
    }

    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return !editor.isOneLineMode() || editor.getSelectionModel().hasSelection();
    }
  }

  private static void duplicateLineOrSelectedBlockAtCaret(Editor editor) {
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    ScrollingModel scrollingModel = editor.getScrollingModel();
    if(editor.getSelectionModel().hasSelection()) {
      int start = editor.getSelectionModel().getSelectionStart();
      int end = editor.getSelectionModel().getSelectionEnd();
      String s = document.getCharsSequence().subSequence(start, end).toString();
      document.insertString(end, s);
      caretModel.moveToOffset(end+s.length());
      scrollingModel.scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
      editor.getSelectionModel().setSelection(end, end+s.length());
    }
    else {
      duplicateLinesRange(editor, document, caretModel.getVisualPosition(), caretModel.getVisualPosition());
    }
  }

  @Nullable
  static Couple<Integer> duplicateLinesRange(Editor editor, Document document, VisualPosition rangeStart, VisualPosition rangeEnd) {
    Pair<LogicalPosition, LogicalPosition> lines = EditorUtil.calcSurroundingRange(editor, rangeStart, rangeEnd);
    int offset = editor.getCaretModel().getOffset();

    LogicalPosition lineStart = lines.first;
    LogicalPosition nextLineStart = lines.second;
    int start = editor.logicalPositionToOffset(lineStart);
    int end = editor.logicalPositionToOffset(nextLineStart);
    if (end <= start) {
      return null;
    }
    String s = document.getCharsSequence().subSequence(start, end).toString();
    final int lineToCheck = nextLineStart.line - 1;

    int newOffset = end + offset - start;
    if(lineToCheck == document.getLineCount () /* empty document */
       || lineStart.line == document.getLineCount() - 1 /* last line*/
       || document.getLineSeparatorLength(lineToCheck) == 0)
    {
      s = "\n"+s;
      newOffset++;
    }
    document.insertString(end, s);

    editor.getCaretModel().moveToOffset(newOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return Couple.of(end, end + s.length());
  }

  @Override
  public void update(final Editor editor, final Presentation presentation, final DataContext dataContext) {
    super.update(editor, presentation, dataContext);
    if (editor.getSelectionModel().hasSelection()) {
      presentation.setText(EditorBundle.message("action.duplicate.block"), true);
    }
    else {
      presentation.setText(EditorBundle.message("action.duplicate.line"), true);
    }
  }
}
