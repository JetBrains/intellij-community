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
class Redo extends UndoRedo {
  Redo(UndoManagerImpl manager, FileEditor editor) {
    super(manager, editor);
  }

  protected UndoRedoStacksHolder getStackHolder() {
    return myManager.getRedoStacksHolder();
  }

  protected UndoRedoStacksHolder getReverseStackHolder() {
    return myManager.getUndoStacksHolder();
  }

  protected String getActionName() {
    return CommonBundle.message("redo.confirmation.title");
  }

  protected String getActionName(String commandName) {
    return CommonBundle.message("redo.command.confirmation.text", commandName);
  }

  protected void performAction() {
    myUndoableGroup.redo();
  }

  protected EditorAndState getBeforeState() {
    return myUndoableGroup.getStateBefore();
  }

  protected EditorAndState getAfterState() {
    return myUndoableGroup.getStateAfter();
  }

  protected void setBeforeState(EditorAndState state) {
    myUndoableGroup.setStateBefore(state);
  }

  @Override
  protected boolean isRedo() {
    return true;
  }
}
