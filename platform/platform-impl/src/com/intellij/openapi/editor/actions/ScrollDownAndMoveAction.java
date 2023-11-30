// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Moves editor viewport one visual line down. Caret is also moved one line down if it becomes off-screen
 */
public final class ScrollDownAndMoveAction extends InactiveEditorAction {
  
  public ScrollDownAndMoveAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      EditorActionUtil.scrollRelatively(editor, 1, 0, true);
    }
  }
}
