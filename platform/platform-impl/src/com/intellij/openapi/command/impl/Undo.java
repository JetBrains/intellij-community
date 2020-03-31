// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author lesya
 */
class Undo extends UndoRedo {
  Undo(UndoManagerImpl manager, FileEditor editor) {
    super(manager, editor);
  }

  @Override
  protected UndoRedoStacksHolder getStackHolder() {
    return myManager.getUndoStacksHolder();
  }

  @Override
  protected UndoRedoStacksHolder getReverseStackHolder() {
    return myManager.getRedoStacksHolder();
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
  protected void performAction() {
    myUndoableGroup.undo();
  }

  @Override
  protected EditorAndState getBeforeState() {
    return myUndoableGroup.getStateAfter();
  }

  @Override
  protected EditorAndState getAfterState() {
    return myUndoableGroup.getStateBefore();
  }

  @Override
  protected void setBeforeState(EditorAndState state) {
    myUndoableGroup.setStateAfter(state);
  }

  @Override
  protected boolean isRedo() {
    return false;
  }
}