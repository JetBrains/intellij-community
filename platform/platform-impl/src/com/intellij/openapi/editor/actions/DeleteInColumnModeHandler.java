// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.Nullable;

public class DeleteInColumnModeHandler extends EditorWriteActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public DeleteInColumnModeHandler(EditorActionHandler handler) {myOriginalHandler = handler;}

  @Override
  public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (editor.isColumnMode() && caret == null && editor.getCaretModel().getCaretCount() > 1) {
      EditorUIUtil.hideCursorInEditor(editor);
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      CopyPasteManager.getInstance().stopKillRings();
      
      editor.getCaretModel().runForEachCaret(c -> {
        int offset = c.getOffset();
        int lineEndOffset = DocumentUtil.getLineEndOffset(offset, editor.getDocument());
        if (offset < lineEndOffset) myOriginalHandler.execute(editor, c, dataContext);
      });
    }
    else {
      myOriginalHandler.execute(editor, caret, dataContext);
    }
  }
}
