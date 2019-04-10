/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.TextRange;

public class DeleteToWordStartAction extends TextComponentEditorAction {
  public DeleteToWordStartAction() {
    super(new Handler(false));
  }

  static class Handler extends EditorWriteActionHandler {

    private final boolean myNegateCamelMode;

    Handler(boolean negateCamelMode) {
      super(true);
      myNegateCamelMode = negateCamelMode;
    }

    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      CopyPasteManager.getInstance().stopKillRings();

      boolean camelMode = editor.getSettings().isCamelWords();
      if (myNegateCamelMode) {
        camelMode = !camelMode;
      }

      if (editor.getSelectionModel().hasSelection()) {
        EditorModificationUtil.deleteSelectedText(editor);
        return;
      }

      deleteToWordStart(editor, camelMode);
    }
  }

  private static void deleteToWordStart(Editor editor, boolean camelMode) {
    final TextRange range = EditorActionUtil.getRangeToWordStart(editor, camelMode, true);
    if (!range.isEmpty()) {
      editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    }
  }
}
