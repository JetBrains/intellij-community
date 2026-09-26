// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DeleteSelectionHandler extends EditorWriteActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public DeleteSelectionHandler(EditorActionHandler handler) {myOriginalHandler = handler;}

  @Override
  public void executeWriteAction(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (caret == null ? editor.getSelectionModel().hasSelection(true) : caret.hasSelection()) {
      EditorUIUtil.hideCursorInEditor(editor);
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      CopyPasteManager.getInstance().stopKillRings();
      CaretAction action = c -> EditorModificationUtil.deleteSelectedText(editor);
      if (caret == null) editor.getCaretModel().runForEachCaret(action);
      else action.perform(caret);
    }
    else {
      myOriginalHandler.execute(editor, caret, dataContext);
    }
  }
}
