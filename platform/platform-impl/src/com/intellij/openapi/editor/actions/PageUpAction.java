// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;

public final class PageUpAction extends EditorAction {
  public PageUpAction() {
    super(new Handler());
  }

  public static final class Handler extends EditorActionHandler.ForEachCaret {
    @Override
    public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      EditorActionUtil.moveCaretPageUp(editor, false);
    }

    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return !editor.isOneLineMode();
    }
  }
}
