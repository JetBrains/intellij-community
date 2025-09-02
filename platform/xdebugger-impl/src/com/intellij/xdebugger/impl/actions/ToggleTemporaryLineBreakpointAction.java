// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.handlers.XToggleLineBreakpointActionHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ToggleTemporaryLineBreakpointAction extends XDebuggerActionBase implements DumbAware, ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  private static final XToggleLineBreakpointActionHandler ourHandler = new XToggleLineBreakpointActionHandler(true);

  public ToggleTemporaryLineBreakpointAction() {
    super(true);
  }
  @Override
  protected @NotNull DebuggerActionHandler getHandler(final @NotNull DebuggerSupport debuggerSupport) {
    return ourHandler;
  }
}
