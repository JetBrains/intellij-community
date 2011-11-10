/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * User: anna
 * Date: 11/8/11
 */
public class FinishMarkAction extends BasicUndoableAction {
  private final Project myProject;
  private final DocumentReference myReference;
  private @NotNull final StartMarkAction myStartAction;

  private FinishMarkAction(Project project, DocumentReference reference, @NotNull StartMarkAction action) {
    super(reference);
    myProject = project;
    myReference = reference;
    myStartAction = action;
  }

  public void setGlobal(boolean isGlobal) {
    myStartAction.setGlobal(isGlobal);
  }

  public void setCommandName(String commandName) {
    myStartAction.setCommandName(commandName);
  }

  public void undo() {
    final UndoRedoStacksHolder holder = ((UndoManagerImpl)UndoManager.getInstance(myProject)).getUndoStacksHolder();
    final LinkedList<UndoableGroup> stack = holder.getStack(myReference);
    for (Iterator<UndoableGroup> it = stack.descendingIterator(); it.hasNext(); ) {
      UndoableGroup undoableGroup = it.next();
      undoableGroup.setMerged4Redo();
      if (undoableGroup.containsAction(myStartAction)) {
        break;
      }
      undoableGroup.setMerged4Undo();
    }
  }

  public void redo() {
  }

  public static void finish(Project project, Editor editor, @Nullable StartMarkAction startAction) {
    if (startAction == null) return;
    DocumentReference reference = DocumentReferenceManager.getInstance().create(editor.getDocument());
    UndoManager.getInstance(project).undoableActionPerformed(new FinishMarkAction(project, reference, startAction));
    StartMarkAction.markFinished();
  }
}
