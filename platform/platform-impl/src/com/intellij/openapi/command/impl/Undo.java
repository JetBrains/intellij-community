/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.command.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.fileEditor.FileEditor;

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
