// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CutAction extends TextComponentEditorAction {
  public CutAction() {
    super(new Handler(), false);
  }

  public static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(final @NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if(!editor.getSelectionModel().hasSelection(true)) {
        if (CopyAction.isSkipCopyPasteForEmptySelection()) {
          return;
        }
        editor.getCaretModel().runForEachCaret(__ -> editor.getSelectionModel().selectLineAtCaret());
      }
      editor.getSelectionModel().copySelectionToClipboard();
      EditorModificationUtil.deleteSelectedTextForAllCarets(editor);
    }
  }
}