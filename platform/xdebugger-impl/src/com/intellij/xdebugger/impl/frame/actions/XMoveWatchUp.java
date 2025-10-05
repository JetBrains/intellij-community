// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.frame.XWatchesViewImpl;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class XMoveWatchUp extends XWatchesTreeActionBase implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  public XMoveWatchUp() {
    getTemplatePresentation().setIcon(CommonActionsPanel.Buttons.UP.getIcon());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree) {
    List<? extends WatchNodeImpl> nodes = getSelectedNodes(tree, WatchNodeImpl.class);
    if (nodes.size() == 1) {
      XDebuggerTreeNode root = tree.getRoot();
      if (root instanceof WatchesRootNode) {
        int firstWatchIndex = ((WatchesRootNode)root).headerNodesCount();
        return root.getIndex(nodes.get(0)) > firstWatchIndex;
      }
    }
    return false;
  }

  @Override
  protected void perform(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree, @NotNull XWatchesView watchesView) {
    if (watchesView instanceof XWatchesViewImpl) {
      ((XWatchesViewImpl)watchesView).moveWatchUp(ContainerUtil.getFirstItem(getSelectedNodes(tree, WatchNodeImpl.class)));
    }
  }
}
