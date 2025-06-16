// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler;
import org.jetbrains.annotations.NotNull;

final class EvaluateAction extends XDebuggerActionBase implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  private static final XDebuggerEvaluateActionHandler ourHandler = new XDebuggerEvaluateActionHandler();

  EvaluateAction() {
    super(true);
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(final @NotNull DebuggerSupport debuggerSupport) {
    return ourHandler;
  }
}
