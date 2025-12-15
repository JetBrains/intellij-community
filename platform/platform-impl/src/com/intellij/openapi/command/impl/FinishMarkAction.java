// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;


public final class FinishMarkAction extends BasicUndoableAction {

  public static void finish(Project project, Editor editor, @Nullable StartMarkAction startAction) {
    if (startAction != null) {
      DocumentReference reference = DocumentReferenceManager.getInstance().create(editor.getDocument());
      FinishMarkAction finishMark = new FinishMarkAction(reference, startAction);
      CommandProcessor.getInstance().executeCommand(
        project,
        () -> {
          UndoManager.getInstance(project).undoableActionPerformed(finishMark);
          StartMarkAction.markFinished(editor);
        },
        IdeBundle.message("command.finish"),
        null
      );
    }
  }

  private final @Nullable StartMarkAction startMark; // can be null only in RD
  private @Nullable @Command String commandName = null;
  private boolean isGlobal = false;

  FinishMarkAction(@NotNull DocumentReference reference, boolean isGlobal) {
    this(reference, null);
    if (isGlobal) {
      markGlobal(null);
    }
  }

  private FinishMarkAction(@NotNull DocumentReference reference, @Nullable StartMarkAction startMark) {
    super(reference);
    this.startMark = startMark;
  }

  @Override
  public void undo() {
  }

  @Override
  public void redo() {
  }

  @Override
  public boolean isGlobal() {
    return isGlobal;
  }

  @Override
  public DocumentReference @NotNull [] getAffectedDocuments() {
    return Objects.requireNonNull(
      super.getAffectedDocuments(),
      "document must be set during instance creation"
    );
  }

  void markGlobal(@Nullable @Command String commandName) {
    if (startMark != null) {
      startMark.setGlobal(true);
      startMark.setCommandName(commandName);
    }
    this.isGlobal = true;
    this.commandName = commandName;
  }

  @Nullable @Command String getCommandName() {
    return commandName;
  }
}
