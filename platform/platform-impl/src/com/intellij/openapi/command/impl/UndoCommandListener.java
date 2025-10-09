// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


final class UndoCommandListener implements CommandListener {

  private static final Logger LOG = Logger.getInstance(UndoCommandListener.class);

  private final @Nullable Project project;
  private final @NotNull UndoManagerImpl undoManager;

  private boolean isTransparentActionStarted;

  @SuppressWarnings("unused")
  UndoCommandListener(@NotNull Project project) {
    this(project, UndoManager.getInstance(project));
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
    if (projectNotDisposed() && !isTransparentActionStarted) {
      undoManager.onCommandStarted(
        event.getProject(),
        event.getUndoConfirmationPolicy(),
        event.shouldRecordActionForOriginalDocument(),
        false
      );
    }
  }

  @Override
  public void commandFinished(@NotNull CommandEvent event) {
    if (projectNotDisposed() && !isTransparentActionStarted) {
      undoManager.onCommandFinished(
        event.getProject(),
        event.getCommandName(),
        event.getCommandGroupId()
      );
    }
  }

  @Override
  public void undoTransparentActionStarted() {
    if (projectNotDisposed() && !undoManager.isInsideCommand()) {
      try {
        undoManager.onCommandStarted(
          project,
          UndoConfirmationPolicy.DEFAULT,
          true,
          true
        );
      } finally {
        isTransparentActionStarted = true;
      }
    }
  }

  @Override
  public void undoTransparentActionFinished() {
    if (projectNotDisposed() && isTransparentActionStarted) {
      try {
        undoManager.onCommandFinished(project, "", null);
      } finally {
        isTransparentActionStarted = false;
      }
    }
  }

  private boolean projectNotDisposed() {
    boolean isDisposed = project != null && project.isDisposed();
    if (isDisposed) {
      LOG.warn("Cannot perform a command, project is disposed " + project);
    }
    return !isDisposed;
  }
}
