// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.textarea.TextComponentEditor;
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler;
import org.jetbrains.annotations.NotNull;

public final class LineEndWithSelectionAction extends TextComponentEditorAction {
  public LineEndWithSelectionAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorActionHandler.ForEachCaret {
    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return !ModifierKeyDoubleClickHandler.getInstance().isRunningAction() ||
             EditorSettingsExternalizable.getInstance().addCaretsOnDoubleCtrl();
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      EditorActionUtil.moveCaretToLineEnd(editor, true, !(editor instanceof TextComponentEditor));
    }
  }
}
