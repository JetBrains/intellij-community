// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author lesya
 */
final class Undo extends UndoRedo {
  Undo(
    @Nullable Project project,
    @Nullable FileEditor editor,
    @NotNull UndoRedoStacksHolder undoStacksHolder,
    @NotNull UndoRedoStacksHolder redoStacksHolder,
    @NotNull SharedUndoRedoStacksHolder sharedUndoStacksHolder,
    @NotNull SharedUndoRedoStacksHolder sharedRedoStacksHolder
  ) {
    super(
      project,
      editor,
      undoStacksHolder,
      redoStacksHolder,
      sharedUndoStacksHolder,
      sharedRedoStacksHolder,
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
  protected @Nullable EditorAndState getBeforeState() {
    return undoableGroup.getStateAfter();
  }

  @Override
  protected @Nullable EditorAndState getAfterState() {
    return undoableGroup.getStateBefore();
  }

  @Override
  protected void setBeforeState(@NotNull EditorAndState state) {
    undoableGroup.setStateAfter(state);
  }
}
