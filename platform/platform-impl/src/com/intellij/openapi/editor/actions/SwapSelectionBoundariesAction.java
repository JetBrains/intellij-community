// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Provides functionality similar to the emacs
 * <a href="http://www.gnu.org/software/emacs/manual/html_node/emacs/Setting-Mark.html">exchange-point-and-mark</a>.
 */
@ApiStatus.Internal
public final class SwapSelectionBoundariesAction extends EditorAction {

  public SwapSelectionBoundariesAction() {
    super(new Handler());
  }
  
  private static final class Handler extends EditorActionHandler.ForEachCaret {
    @Override
    public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      if (!caret.hasSelection()) {
        return;
      }
      final int start = caret.getSelectionStart();
      final int end = caret.getSelectionEnd();
      boolean moveToEnd = caret.getOffset() == start;
      
      if (editor instanceof EditorEx editorEx) {
        if (editorEx.isStickySelection()) {
          editorEx.setStickySelection(false);
          editorEx.setStickySelection(true);
        }
      }
      
      if (moveToEnd) {
        caret.moveToOffset(end);
      }
      else {
        caret.moveToOffset(start);
      }
    }
  }
}
