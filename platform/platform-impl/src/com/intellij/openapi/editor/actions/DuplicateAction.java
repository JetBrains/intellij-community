/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    int newOffset = end + offset - start;
    int selectionStart = end;
    if (nextLineStart.line == document.getLineCount() - 1 && nextLineStart.column > 0) { // last line
      s = "\n"+s;
      newOffset++;
      selectionStart++;
    }
    document.insertString(end, s);

    editor.getCaretModel().moveToOffset(newOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return Couple.of(selectionStart, end + s.length());
  }

  @Override
  public void update(final Editor editor, final Presentation presentation, final DataContext dataContext) {
    super.update(editor, presentation, dataContext);
    if (editor.getSelectionModel().hasSelection()) {
      presentation.setText(EditorBundle.message("action.duplicate.selection"), true);
    }
    else {
      presentation.setText(EditorBundle.message("action.duplicate.line"), true);
    }
  }
}
