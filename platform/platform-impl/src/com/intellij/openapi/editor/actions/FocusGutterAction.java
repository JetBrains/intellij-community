// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Requests focus to editor gutter to support a11y for Screen Reader.
 *
 * @author tav
 */
@ApiStatus.Internal
public final class FocusGutterAction extends EditorAction {
  public FocusGutterAction() {
    super(new EditorActionHandler() {
      @Override
      protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
        IdeFocusManager.getGlobalInstance().requestFocus(((EditorImpl)editor).getGutterComponentEx(), true);
      }
      @Override
      protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
        return ScreenReader.isActive();
      }
    });
  }
}
