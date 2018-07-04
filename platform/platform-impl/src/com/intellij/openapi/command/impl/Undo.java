// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.text.StringUtil;

/**
 * author: lesya
 */
class Undo extends UndoRedo {
  public Undo(UndoManagerImpl manager, FileEditor editor) {
    super(manager, editor);
  }

  protected UndoRedoStacksHolder getStackHolder() {
    return myManager.getUndoStacksHolder();
  }

  protected UndoRedoStacksHolder getReverseStackHolder() {
    return myManager.getRedoStacksHolder();
  }

  protected String getActionName() {
    return CommonBundle.message("undo.dialog.title");
  }

  protected String getActionName(String commandName) {
    if (StringUtil.isEmpty(commandName))commandName = ActionsBundle.message("action.undo.description.empty");
    return CommonBundle.message("undo.command.confirmation.text", commandName);
  }

  protected void performAction() {
    myUndoableGroup.undo();
  }

  protected EditorAndState getBeforeState() {
    return myUndoableGroup.getStateAfter();
  }

  protected EditorAndState getAfterState() {
    return myUndoableGroup.getStateBefore();
  }

  protected void setBeforeState(EditorAndState state) {
    myUndoableGroup.setStateAfter(state);
  }

  @Override
  protected boolean isRedo() {
    return false;
  }
}
