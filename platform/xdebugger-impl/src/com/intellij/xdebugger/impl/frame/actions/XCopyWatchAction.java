// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

public class XCopyWatchAction extends XWatchesTreeActionBase implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree) {
    return !getSelectedNodes(tree, XValueNodeImpl.class).isEmpty();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  protected void perform(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree, @NotNull XWatchesView watchesView) {
    XDebuggerTreeNode root = tree.getRoot();
    for (XValueNodeImpl node : getSelectedNodes(tree, XValueNodeImpl.class)) {
      int index = node instanceof WatchNode ? root.getIndex(node) + 1 : -1;
      node.calculateEvaluationExpression().onSuccess(expr -> {
        XExpression watchExpression = expr != null ? expr : XExpressionImpl.fromText(node.getName());
        if (watchExpression != null) {
          DebuggerUIUtil.invokeLater(() -> watchesView.addWatchExpression(watchExpression, index, true));
        }
      });
    }
  }
}
