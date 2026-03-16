// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.impl.cmd.CmdEvent;
import com.intellij.openapi.command.impl.cmd.CmdEventTransparent;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;


final class UndoCommandListener implements SeparatedCommandListener {
  private final @NotNull ComponentManager componentManager;
  private final @NotNull UndoManagerImpl undoManager;

  @SuppressWarnings("unused")
  UndoCommandListener(@NotNull Project project) {
    this(project, UndoManager.getInstance(project));
  }

  @SuppressWarnings("unused")
  UndoCommandListener() {
    this(ApplicationManager.getApplication(), UndoManager.getGlobalInstance());
  }

  private UndoCommandListener(
    @NotNull ComponentManager componentManager,
    @NotNull UndoManager undoManager
  ) {
    this.componentManager = componentManager;
    this.undoManager = (UndoManagerImpl)undoManager;
  }

  @Override
  public void onCommandStarted(@NotNull CmdEvent cmdStartEvent) {
    safePerform(
      () -> undoManager.onCommandStarted(eventWithProject(cmdStartEvent))
    );
  }

  @Override
  public void onCommandFinished(@NotNull CmdEvent cmdFinishEvent) {
    safePerform(
      () -> undoManager.onCommandFinished(eventWithProject(cmdFinishEvent))
    );
  }

  @Override
  public void onCommandFakeFinished(@NotNull CmdEvent cmdFakeFinishEvent) {
    safePerform(
      () -> undoManager.onCommandFakeFinished(cmdFakeFinishEvent)
    );
  }

  private @NotNull CmdEvent eventWithProject(@NotNull CmdEvent cmdEvent) {
    if (cmdEvent.isTransparent()) {
      var project = (componentManager instanceof Project) ? (Project) componentManager : null;
      return ((CmdEventTransparent) cmdEvent).withProject(project);
    }
    return cmdEvent;
  }

  private void safePerform(@NotNull Runnable runnable) {
    if (componentManager.isDisposed()) {
      throw new UndoIllegalStateException(
        "Cannot perform a command, corresponding componentManager is disposed " + componentManager
      );
    }
    try {
      runnable.run();
    } catch (Throwable e) {
      if (isInvisible(e)) {
        throw new UndoIllegalStateException(
          "CE or ControlFlow exception occurred, UndoManager may be in inconsistent state", e
        );
      }
      throw e;
    }
  }

  private static boolean isInvisible(Throwable e) {
    return e instanceof CancellationException ||
           e instanceof ControlFlowException;
  }
}
