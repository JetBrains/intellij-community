// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This action works only in the variables view
 * @see com.intellij.xdebugger.impl.actions.AddToWatchesAction
 */
@ApiStatus.Internal
public class XAddToWatchesTreeAction extends XDebuggerTreeActionBase {
  @Override
  protected boolean isEnabled(final @NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    return super.isEnabled(node, e) && DebuggerUIUtil.getWatchesView(e) != null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    for (XValueNodeImpl node : getSelectedNodes(e.getDataContext())) {
      if (node != null) {
        String nodeName = node.getName();
        if (nodeName != null) {
          perform(node, nodeName, e);
        }
      }
    }
  }

  @Override
  protected void perform(final XValueNodeImpl node, final @NotNull String nodeName, final AnActionEvent e) {
    final XWatchesView watchesView = DebuggerUIUtil.getWatchesView(e);
    if (watchesView != null) {
      DebuggerUIUtil.addToWatches(watchesView, node);
    }
  }
}