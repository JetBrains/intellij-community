// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.ui.tree.actions.XAddToWatchesTreeAction;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import org.jetbrains.annotations.NotNull;

final class AddToWatchesAction extends XDebuggerActionBase {
  private static class Holder {
    private static final XAddToWatchesTreeAction TREE_ACTION = new XAddToWatchesTreeAction();
  }

  AddToWatchesAction() {
    super(true);
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return debuggerSupport.getAddToWatchesActionHandler();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    if (XDebuggerTreeActionBase.getSelectedNode(event.getDataContext()) != null) {
      Holder.TREE_ACTION.update(event);
    }
    else {
      super.update(event);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (XDebuggerTreeActionBase.getSelectedNode(event.getDataContext()) != null) {
      Holder.TREE_ACTION.actionPerformed(event);
    }
    else {
      super.actionPerformed(event);
    }
  }
}
