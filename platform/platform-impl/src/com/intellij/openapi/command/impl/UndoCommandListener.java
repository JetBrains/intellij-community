// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.impl.cmd.CmdEvent;
import com.intellij.openapi.command.impl.cmd.CmdEventTransparent;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


final class UndoCommandListener implements SeparatedCommandListener {

  private static final Logger LOG = Logger.getInstance(UndoCommandListener.class);

  private final @Nullable Project project;
  private final @NotNull UndoManagerImpl undoManager;

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
  public void onCommandStarted(@NotNull CmdEvent cmdStartEvent) {
    if (projectNotDisposed()) {
      undoManager.onCommandStarted(eventWithProject(cmdStartEvent));
    }
  }

  @Override
  public void onCommandFinished(@NotNull CmdEvent cmdFinishEvent) {
    if (projectNotDisposed()) {
      undoManager.onCommandFinished(eventWithProject(cmdFinishEvent));
    }
  }

  @Override
  public void onCommandFakeFinished(@NotNull CmdEvent cmdFakeFinishEvent) {
    if (projectNotDisposed()) {
      undoManager.onCommandFakeFinished(cmdFakeFinishEvent);
    }
  }

  private @NotNull CmdEvent eventWithProject(@NotNull CmdEvent cmdEvent) {
    return cmdEvent.isTransparent()
           ? ((CmdEventTransparent) cmdEvent).withProject(project)
           : cmdEvent;
  }

  private boolean projectNotDisposed() {
    boolean isDisposed = project != null && project.isDisposed();
    if (isDisposed) {
      LOG.error("Cannot perform a command, project is disposed " + project);
    }
    return !isDisposed;
  }
}
