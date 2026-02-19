// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class UnselectWordAtCaretAction extends EditorAction implements DumbAware {
  public UnselectWordAtCaretAction() {
    super(new Handler());
    setInjectedContext(true);
  }

  @Override
  protected @Nullable Editor getEditor(@NotNull DataContext dataContext) {
    return TextComponentEditorAction.getEditorFromContext(dataContext);
  }

  private static final class Handler extends EditorActionHandler.ForEachCaret {
    @Override
    public void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    }
  }
}
