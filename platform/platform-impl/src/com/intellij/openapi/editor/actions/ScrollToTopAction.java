// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Scrolls to the top of the target editor without changing its caret position.
 */
@ApiStatus.Internal
public final class ScrollToTopAction extends InactiveEditorAction {

  public ScrollToTopAction() {
    super(new MyHandler());
  }
  
  private static final class MyHandler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      editor.getScrollingModel().scrollVertically(0);
    }
  }
}
