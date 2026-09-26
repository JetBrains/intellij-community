// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.handlers.XEvaluateInConsoleFromEditorActionHandler;
import org.jetbrains.annotations.NotNull;

final class EvaluateInConsoleAction extends XDebuggerActionBase implements SplitDebuggerAction {
  private static final DebuggerActionHandler ourHandler = new XEvaluateInConsoleFromEditorActionHandler();

  EvaluateInConsoleAction() {
    super(true);
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return ourHandler;
  }
}
