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

public class XMoveWatchDown extends XWatchesTreeActionBase implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  public XMoveWatchDown() {
    getTemplatePresentation().setIcon(CommonActionsPanel.Buttons.DOWN.getIcon());
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
      if (root instanceof WatchesRootNode rootNode) {
        int size = rootNode.getWatchChildren().size() - 1 + rootNode.headerNodesCount();
        return root.getIndex(nodes.get(0)) < size;
      }
    }
    return false;
  }

  @Override
  protected void perform(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree, @NotNull XWatchesView watchesView) {
    if (watchesView instanceof XWatchesViewImpl) {
      ((XWatchesViewImpl)watchesView).moveWatchDown(ContainerUtil.getFirstItem(getSelectedNodes(tree, WatchNodeImpl.class)));
    }
  }
}
