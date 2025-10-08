// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase;
import com.intellij.xdebugger.impl.actions.handlers.XAddToInlineWatchesFromEditorActionHandler;
import org.jetbrains.annotations.NotNull;

final class AddInlineWatchAction extends XDebuggerActionBase implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  private static final XAddToInlineWatchesFromEditorActionHandler ourHandler = new XAddToInlineWatchesFromEditorActionHandler();

  AddInlineWatchAction() {
    super(true);
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return ourHandler;
  }
}
