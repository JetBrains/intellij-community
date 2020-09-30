// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class InstancesWindowBase extends DialogWrapper {

  protected static final int DEFAULT_WINDOW_WIDTH = 870;
  protected static final int DEFAULT_WINDOW_HEIGHT = 400;

  protected final String className;

  public InstancesWindowBase(@NotNull XDebugSession session,
                             @NotNull String className) {
    super(session.getProject(), false);
    this.className = className;

    addWarningMessage(null);
    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionStopped() {
        ApplicationManager.getApplication().invokeLater(() -> close(OK_EXIT_CODE));
      }
    }, myDisposable);
    setModal(false);
  }

  protected void addWarningMessage(@Nullable String message) {
    setTitle(message == null ?
             XDebuggerBundle.message("memory.view.instances.dialog.title", className) :
             XDebuggerBundle.message("memory.view.instances.dialog.title.warning", className, message));
  }

  @NotNull
  @Override
  protected String getDimensionServiceKey() {
    return "#org.jetbrains.debugger.memory.view.InstancesWindow";
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{new DialogWrapperExitAction(XDebuggerBundle.message("memory.instances.close.text"), CLOSE_EXIT_CODE)};
  }
}
