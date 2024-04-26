// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.textarea.TextComponentEditor;
import org.jetbrains.annotations.NotNull;

public final class LineEndAction extends TextComponentEditorAction {
  public LineEndAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorActionHandler.ForEachCaret {
    @Override
    protected void doExecute(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      EditorActionUtil.moveCaretToLineEnd(editor, false, !(editor instanceof TextComponentEditor));
    }
  }
}
