// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCopyPasteHelper;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actions.CopyAction.SelectionToCopy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CutAction extends TextComponentEditorAction {
  public CutAction() {
    super(new Handler(), false);
  }

  public static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(final @NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      SelectionToCopy selectionToCopy = CopyAction.prepareSelectionToCut(editor);
      if (selectionToCopy == null) {
        return;
      }
      CopyAction.copyToClipboard(editor, EditorCopyPasteHelper.getInstance()::getSelectionTransferable, selectionToCopy);
      EditorModificationUtil.deleteSelectedTextForAllCarets(editor);
    }
  }
}