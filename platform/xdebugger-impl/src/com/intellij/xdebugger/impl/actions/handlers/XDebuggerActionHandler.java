// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An abstract handler for debugger-specific actions within the IntelliJ platform.
 * This class bridges action execution and the debugging sessions, providing a mechanism to perform
 * and determine the enablement state of debugger actions tied to a specific debugging session.
 * <p>
 * The handler is supposed to work with {@link com.intellij.xdebugger.impl.actions.XDebuggerActionBase}.
 * This handler can be used in monolith or backend IDE but will not work on the frontend.
 *
 * @see XDebuggerSplitActionHandler for frontend usages
 */
public abstract class XDebuggerActionHandler extends DebuggerActionHandler {
  @Override
  public void perform(@NotNull Project project, @NotNull AnActionEvent event) {
    XDebugSession session = DebuggerUIUtil.getSession(event);
    if (session != null) {
      perform(session, event.getDataContext());
    }
  }

  @Override
  public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
    if (LightEdit.owns(project)) return false;
    XDebugSession session = DebuggerUIUtil.getSession(event);
    return session != null && isEnabled(session, event.getDataContext());
  }

  @ApiStatus.OverrideOnly
  protected abstract boolean isEnabled(@NotNull XDebugSession session, @NotNull DataContext dataContext);

  @ApiStatus.OverrideOnly
  protected abstract void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext);
}
