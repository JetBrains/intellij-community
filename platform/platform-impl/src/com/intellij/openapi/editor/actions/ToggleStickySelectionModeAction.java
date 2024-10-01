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
import org.jetbrains.annotations.Nullable;

/**
 * Allows to toggle {@link EditorEx#isStickySelection() sticky selection} for editors.
 * <p/>
 * Thread-safe.
 */
@ApiStatus.Internal
public final class ToggleStickySelectionModeAction extends EditorAction {

  public ToggleStickySelectionModeAction() {
    super(new Handler());
  }

  static final class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (!(editor instanceof EditorEx ex)) {
        return;
      }

      ex.setStickySelection(!ex.isStickySelection());
    }
  }
}
