// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author lesya
 */
final class Redo extends UndoRedo {
  Redo(UndoClientState state, FileEditor editor) {
    super(state, editor);
  }

  @Override
  protected UndoRedoStacksHolder getStacksHolder() {
    return myState.getRedoStacksHolder();
  }

  @Override
  protected UndoRedoStacksHolder getReverseStacksHolder() {
    return myState.getUndoStacksHolder();
  }

  @Override
  protected SharedUndoRedoStacksHolder getSharedStacksHolder() {
    return myManager.getSharedRedoStacksHolder();
  }

  @Override
  protected SharedUndoRedoStacksHolder getSharedReverseStacksHolder() {
    return myManager.getSharedUndoStacksHolder();
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
    myUndoableGroup.redo();
  }

  @Override
  protected EditorAndState getBeforeState() {
    return myUndoableGroup.getStateBefore();
  }

  @Override
  protected EditorAndState getAfterState() {
    return myUndoableGroup.getStateAfter();
  }

  @Override
  protected void setBeforeState(EditorAndState state) {
    myUndoableGroup.setStateBefore(state);
  }

  @Override
  protected boolean isRedo() {
    return true;
  }
}