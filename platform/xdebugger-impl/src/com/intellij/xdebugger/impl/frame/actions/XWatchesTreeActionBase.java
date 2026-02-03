// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

public abstract class XWatchesTreeActionBase extends AnAction implements DumbAware {
  public static @NotNull <T extends TreeNode> List<? extends T> getSelectedNodes(final @NotNull XDebuggerTree tree, Class<? extends T> nodeClass) {
    List<T> list = new ArrayList<>();
    TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths != null) {
      for (TreePath selectionPath : selectionPaths) {
        Object element = selectionPath.getLastPathComponent();
        if (nodeClass.isInstance(element)) {
          list.add(nodeClass.cast(element));
        }
      }
    }
    return list;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    final XDebuggerTree tree = XDebuggerTree.getTree(e);
    XWatchesView watchesView = e.getData(XWatchesView.DATA_KEY);
    boolean enabled = tree != null && watchesView != null && isEnabled(e, tree);
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final XDebuggerTree tree = XDebuggerTree.getTree(e);
    XWatchesView watchesView = e.getData(XWatchesView.DATA_KEY);
    if (tree != null && watchesView != null) {
      perform(e, tree, watchesView);
    }
  }

  protected abstract void perform(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree, @NotNull XWatchesView watchesView);

  protected boolean isEnabled(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree) {
    return true;
  }
}
