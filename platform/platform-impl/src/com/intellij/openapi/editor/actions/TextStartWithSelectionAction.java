// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TextStartWithSelectionAction extends TextComponentEditorAction {
  public TextStartWithSelectionAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      List<Caret> carets = editor.getCaretModel().getAllCarets();
      if (editor.isColumnMode() && editor.getCaretModel().supportsMultipleCarets()) {
        if (caret == null) { // normally we're always called with null caret
          caret = carets.get(0) == editor.getCaretModel().getPrimaryCaret() ? carets.get(carets.size() - 1) : carets.get(0);
        }
        LogicalPosition leadSelectionPosition = editor.visualToLogicalPosition(caret.getLeadSelectionPosition());
        LogicalPosition targetPosition = new LogicalPosition(0, 0);
        editor.getSelectionModel().setBlockSelection(leadSelectionPosition, targetPosition);
      }
      else {
        if (caret == null) { // normally we're always called with null caret
          caret = carets.get(carets.size() - 1);
        }
        int selectionStart = caret.getLeadSelectionOffset();
        caret.moveToOffset(0);
        caret.setSelection(selectionStart, 0);
      }
      ScrollingModel scrollingModel = editor.getScrollingModel();
      scrollingModel.disableAnimation();
      scrollingModel.scrollToCaret(ScrollType.RELATIVE);
      scrollingModel.enableAnimation();
    }
  }
}
