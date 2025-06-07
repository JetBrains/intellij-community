// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


final class UndoCommandListener implements CommandListener {
  private final @Nullable Project project;
  private final @NotNull UndoManagerImpl undoManager;
  private boolean isStarted;

  @SuppressWarnings("unused")
  UndoCommandListener(Project project) {
    this(project, project.getService(UndoManager.class));
  }

  @SuppressWarnings("unused")
  UndoCommandListener() {
    this(null, UndoManager.getGlobalInstance());
  }

  private UndoCommandListener(@Nullable Project project, @NotNull UndoManager undoManager) {
    this.project = project;
    this.undoManager = (UndoManagerImpl)undoManager;
  }

  @Override
  public void commandStarted(@NotNull CommandEvent event) {
    if (!isStarted && !isProjectDisposed()) {
      undoManager.onCommandStarted(event.getProject(), event.getUndoConfirmationPolicy(), event.shouldRecordActionForOriginalDocument());
    }
  }

  @Override
  public void commandFinished(@NotNull CommandEvent event) {
    if (!isStarted && !isProjectDisposed()) {
      undoManager.onCommandFinished(event.getProject(), event.getCommandName(), event.getCommandGroupId());
    }
  }

  @Override
  public void undoTransparentActionStarted() {
    if (!isProjectDisposed() && !undoManager.isInsideCommand()) {
      isStarted = true;
      undoManager.onCommandStarted(project, UndoConfirmationPolicy.DEFAULT, true);
    }
  }

  @Override
  public void undoTransparentActionFinished() {
    if (isStarted && !isProjectDisposed()) {
      isStarted = false;
      undoManager.onCommandFinished(project, "", null);
    }
  }

  private boolean isProjectDisposed() {
    return project != null && project.isDisposed();
  }
}
