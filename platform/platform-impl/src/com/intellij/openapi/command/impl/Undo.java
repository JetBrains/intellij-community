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
final class Undo extends UndoRedo {
  Undo(@NotNull UndoClientState state, @Nullable FileEditor editor) {
    super(
      state.getProject(),
      editor,
      state.getUndoStacksHolder(),
      state.getRedoStacksHolder(),
      state.getUndoManager().getSharedUndoStacksHolder(),
      state.getUndoManager().getSharedRedoStacksHolder(),
      false
    );
  }

  @Override
  protected String getActionName() {
    return IdeBundle.message("undo.dialog.title");
  }

  @Override
  protected String getActionName(String commandName) {
    if (StringUtil.isEmpty(commandName)) commandName = ActionsBundle.message("action.undo.description.empty");
    return IdeBundle.message("undo.command", commandName);
  }

  @Override
  protected void performAction() throws UnexpectedUndoException {
    undoableGroup.undo();
  }

  @Override
  protected EditorAndState getBeforeState() {
    return undoableGroup.getStateAfter();
  }

  @Override
  protected EditorAndState getAfterState() {
    return undoableGroup.getStateBefore();
  }

  @Override
  protected void setBeforeState(EditorAndState state) {
    undoableGroup.setStateAfter(state);
  }
}
