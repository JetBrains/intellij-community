// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public class XEditWatchAction extends XWatchesTreeActionBase implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  @Override
  public void update(final @NotNull AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    e.getPresentation().setEnabledAndVisible(tree != null && getSelectedNodes(tree, WatchNodeImpl.class).size() == 1);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
  @Override
  protected void perform(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree, @NotNull XWatchesView watchesView) {
    List<? extends WatchNodeImpl> watchNodes = getSelectedNodes(tree, WatchNodeImpl.class);
    if (watchNodes.size() != 1) return;

    WatchNodeImpl node = watchNodes.get(0);
    XDebuggerTreeNode root = tree.getRoot();
    if (root instanceof WatchesRootNode watchesRootNode) {
      watchesRootNode.editWatch(node);
    }
  }
}
