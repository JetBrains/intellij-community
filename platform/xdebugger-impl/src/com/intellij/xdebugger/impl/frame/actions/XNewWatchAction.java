// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.frame.XVariablesViewBase;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class XNewWatchAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    XWatchesView view = DebuggerUIUtil.getWatchesView(e);
    if (view instanceof XVariablesViewBase) {
      XDebuggerTreeNode root = ((XVariablesViewBase)view).getTree().getRoot();
      if (root instanceof WatchesRootNode watchesRootNode) {
        XDebugSessionProxy proxy = DebuggerUIUtil.getSessionProxy(e);
        if (proxy != null) {
          XDebugSessionTab.showWatchesView(proxy);
        }
        watchesRootNode.addNewWatch();
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(DebuggerUIUtil.getSessionProxy(e) != null && DebuggerUIUtil.getWatchesView(e) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
