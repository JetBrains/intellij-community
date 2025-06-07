// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class XDebuggerTreeActionBase extends AnAction {
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    XValueNodeImpl node = getSelectedNode(e.getDataContext());
    if (node != null) {
      String nodeName = node.getName();
      if (nodeName != null) {
        perform(node, nodeName, e);
      }
    }
  }

  protected abstract void perform(final XValueNodeImpl node, @NotNull String nodeName, final AnActionEvent e);

  @Override
  public void update(final @NotNull AnActionEvent e) {
    XValueNodeImpl node = getSelectedNode(e.getDataContext());
    e.getPresentation().setEnabled(node != null && isEnabled(node, e));
  }

  protected boolean isEnabled(final @NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    return node.getName() != null;
  }

  public static @NotNull List<XValueNodeImpl> getSelectedNodes(@NotNull DataContext dataContext) {
    return XDebuggerTree.getSelectedNodes(dataContext);
  }

  public static @Nullable XValueNodeImpl getSelectedNode(@NotNull DataContext dataContext) {
    return ContainerUtil.getFirstItem(getSelectedNodes(dataContext));
  }

  public static @Nullable XValue getSelectedValue(@NotNull DataContext dataContext) {
    XValueNodeImpl node = getSelectedNode(dataContext);
    return node != null ? node.getValueContainer() : null;
  }
}