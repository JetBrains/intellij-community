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

/**
 * User: anna
 * Date: 11/8/11
 */
public class FinishMarkAction extends BasicUndoableAction {
  private @NotNull final StartMarkAction myStartAction;
  private boolean myGlobal = false;
  private String myCommandName;
  private DocumentReference myReference;

  private FinishMarkAction(DocumentReference reference, @NotNull StartMarkAction action) {
    super(reference);
    myReference = reference;
    myStartAction = action;
  }

  public void undo() {
  }

  public void redo() {
  }

  public boolean isGlobal() {
    return myGlobal;
  }

  public void setGlobal(boolean isGlobal) {
    myStartAction.setGlobal(isGlobal);
    myGlobal = isGlobal;
  }

  public void setCommandName(String commandName) {
    myStartAction.setCommandName(commandName);
    myCommandName = commandName;
  }

  public String getCommandName() {
    return myCommandName;
  }

  public DocumentReference getAffectedDocument() {
    return myReference;
  }

  public static void finish(Project project, Editor editor, @Nullable StartMarkAction startAction) {
    if (startAction == null) return;
    DocumentReference reference = DocumentReferenceManager.getInstance().create(editor.getDocument());
    UndoManager.getInstance(project).undoableActionPerformed(new FinishMarkAction(reference, startAction));
    StartMarkAction.markFinished();
  }
}
