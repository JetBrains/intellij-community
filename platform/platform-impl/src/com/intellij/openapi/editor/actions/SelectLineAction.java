// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SelectLineAction extends TextComponentEditorAction implements ActionRemoteBehaviorSpecification.Frontend {
  public SelectLineAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorActionHandler.ForEachCaret {
    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      editor.getSelectionModel().selectLineAtCaret();
      EditorActionUtil.moveCaretToLineStartIgnoringSoftWraps(editor);
    }
  }
}
