// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public abstract class ConvertIndentsActionBase extends EditorAction {
  protected ConvertIndentsActionBase() {
    super(null);
    setupHandler(new Handler());
  }

  protected abstract int performAction(Editor editor, TextRange textRange);

  private final class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(final @NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      int changedLines = 0;
      if (selectionModel.hasSelection()) {
        changedLines = performAction(editor, new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()));
      }
      else {
        changedLines += performAction(editor, new TextRange(0, editor.getDocument().getTextLength()));
      }
      if (changedLines == 0) {
        HintManager.getInstance().showInformationHint(editor, IdeBundle.message("hint.text.all.lines.already.have.requested.indentation"));
      }
      else {
        HintManager.getInstance().showInformationHint(editor, IdeBundle.message("hint.text.changed.indentation.in", changedLines));
      }
    }
  }
}
