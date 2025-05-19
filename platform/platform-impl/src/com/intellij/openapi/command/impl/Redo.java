// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author lesya
 */
final class Redo extends UndoRedo {
  Redo(@NotNull UndoClientState state, @Nullable FileEditor editor) {
    super(
      state.getProject(),
      editor,
      state.getRedoStacksHolder(),
      state.getUndoStacksHolder(),
      state.getUndoManager().getSharedRedoStacksHolder(),
      state.getUndoManager().getSharedUndoStacksHolder(),
      true
    );
  }

  @Override
  protected String getActionName() {
    return IdeBundle.message("redo.dialog.title");
  }

  @Override
  protected String getActionName(String commandName) {
    if (StringUtil.isEmpty(commandName)) commandName = ActionsBundle.message("action.redo.description.empty");
    return IdeBundle.message("redo.command", commandName);
  }

  @Override
  protected void performAction() throws UnexpectedUndoException {
    undoableGroup.redo();
  }

  @Override
  protected EditorAndState getBeforeState() {
    return undoableGroup.getStateBefore();
  }

  @Override
  protected EditorAndState getAfterState() {
    return undoableGroup.getStateAfter();
  }

  @Override
  protected void setBeforeState(EditorAndState state) {
    undoableGroup.setStateBefore(state);
  }
}
