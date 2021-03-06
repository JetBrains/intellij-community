// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;

public class MoveUpWithSelectionAndScrollAction extends EditorAction {
  public MoveUpWithSelectionAndScrollAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorActionHandler.ForEachCaret {
    @Override
    public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      if (caret == editor.getCaretModel().getPrimaryCaret()) {
        EditorActionUtil.moveCaretRelativelyAndScroll(editor, 0, -1, true);
      }
      else {
        editor.getCaretModel().moveCaretRelatively(0, -1, true, false, false);
      }
    }
  }
}
