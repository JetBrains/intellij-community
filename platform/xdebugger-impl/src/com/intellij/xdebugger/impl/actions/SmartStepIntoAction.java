// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerSmartStepIntoHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SmartStepIntoAction extends XDebuggerActionBase implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  @ApiStatus.Internal
  public static final DebuggerActionHandler HANDLER = new XDebuggerSmartStepIntoHandler();

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    // Avoid additional `performDebuggerAction` call
    performWithHandler(e);
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(final @NotNull DebuggerSupport debuggerSupport) {
    return HANDLER;
  }
}
