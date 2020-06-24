// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FinishMarkAction extends BasicUndoableAction {
  private @NotNull final StartMarkAction myStartAction;
  private boolean myGlobal = false;
  private String myCommandName;
  private final DocumentReference myReference;

  private FinishMarkAction(DocumentReference reference, @NotNull StartMarkAction action) {
    super(reference);
    myReference = reference;
    myStartAction = action;
  }

  @Override
  public void undo() {
  }

  @Override
  public void redo() {
  }

  @Override
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

  public static void finish(final Project project, final Editor editor, @Nullable final StartMarkAction startAction) {
    if (startAction == null) return;
    CommandProcessor.getInstance().executeCommand(project, () -> {
      DocumentReference reference = DocumentReferenceManager.getInstance().create(editor.getDocument());
      UndoManager.getInstance(project).undoableActionPerformed(new FinishMarkAction(reference, startAction));
      StartMarkAction.markFinished(project);
    }, IdeBundle.message("command.finish"), null);
  }
}
