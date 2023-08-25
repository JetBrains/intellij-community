// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EscapeAction extends EditorAction {
  public EscapeAction() {
    super(new Handler());
    setInjectedContext(true);
  }

  private static final class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (editor instanceof EditorEx editorEx) {
        if (editorEx.isStickySelection()) {
          editorEx.setStickySelection(false);
        }
      }
      boolean scrollNeeded = editor.getCaretModel().getCaretCount() > 1;
      retainOldestCaret(editor.getCaretModel());
      editor.getSelectionModel().removeSelection();
      if (scrollNeeded) {
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }

    private static void retainOldestCaret(CaretModel caretModel) {
      while(caretModel.getCaretCount() > 1) {
        caretModel.removeCaret(caretModel.getPrimaryCaret());
      }
    }

    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      if (editor.isOneLineMode() && Boolean.TRUE.equals(PlatformCoreDataKeys.IS_MODAL_CONTEXT.getData(dataContext))) return false;
      SelectionModel selectionModel = editor.getSelectionModel();
      CaretModel caretModel = editor.getCaretModel();
      return selectionModel.hasSelection() || caretModel.getCaretCount() > 1;
    }
  }
}