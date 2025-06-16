// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public final class DuplicateAction extends EditorAction {
  public DuplicateAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorWriteActionHandler.ForEachCaret {
    @Override
    public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      duplicateLineOrSelectedBlockAtCaret(editor);
    }

    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return !editor.isOneLineMode() || editor.getSelectionModel().hasSelection();
    }

    @Override
    public boolean reverseCaretOrder() {
      return true;
    }
  }

  public static void duplicateLineOrSelectedBlockAtCaret(Editor editor) {
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    ScrollingModel scrollingModel = editor.getScrollingModel();
    if (editor.getSelectionModel().hasSelection()) {
      int start = editor.getSelectionModel().getSelectionStart();
      int end = editor.getSelectionModel().getSelectionEnd();
      String s = document.getCharsSequence().subSequence(start, end).toString();
      document.insertString(end, s);
      caretModel.moveToOffset(end + s.length());
      scrollingModel.scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
      editor.getSelectionModel().setSelection(end, end+s.length());
    }
    else {
      duplicateLinesRange(editor, caretModel.getVisualPosition(), caretModel.getVisualPosition());
    }
  }

  static @NotNull TextRange duplicateLinesRange(@NotNull Editor editor,
                                                @NotNull VisualPosition rangeStart,
                                                @NotNull VisualPosition rangeEnd) {
    TextRange range = EditorUtil.calcSurroundingTextRange(editor, rangeStart, rangeEnd);
    String s = editor.getDocument().getText(range);
    int offset = editor.getCaretModel().getOffset();
    int newOffset = offset + range.getLength();
    int selectionStart = range.getEndOffset();
    if (!s.endsWith("\n")) { // last line
      s = "\n"+s;
      newOffset++;
      selectionStart++;
    }
    DocumentGuardedTextUtil.insertString(editor.getDocument(), range.getEndOffset(), s);

    editor.getCaretModel().moveToOffset(newOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    return TextRange.create(selectionStart, range.getEndOffset() + s.length());
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
