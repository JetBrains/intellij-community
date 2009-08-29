package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;

/**
 * @author nik
 */
public class XNewWatchAction extends XWatchesTreeActionBase {
  public void actionPerformed(final AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    if (tree == null) return;

    XDebuggerTreeNode root = tree.getRoot();
    if (root instanceof WatchesRootNode) {
      final WatchesRootNode watchesRoot = (WatchesRootNode)root;
      watchesRoot.addNewWatch();
    }
  }

  protected boolean isEnabled(final AnActionEvent e) {
    return XDebuggerTree.getTree(e) != null;
  }
}
