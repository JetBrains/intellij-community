// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;

public class ExtendLineSelectionAction extends TextComponentEditorAction {
  public ExtendLineSelectionAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler.ForEachCaret {
    @Override
    public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      EditorActionUtil.selectEntireLines(caret);
      final int end = caret.getSelectionEnd();
      caret.moveToOffset(end);
    }
  }
}
