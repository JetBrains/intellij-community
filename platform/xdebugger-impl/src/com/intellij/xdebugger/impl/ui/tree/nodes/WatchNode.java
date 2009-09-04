package com.intellij.xdebugger.impl.ui.tree.nodes;

import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;

/**
 * @author nik
 */
public interface WatchNode extends TreeNode {

  @NotNull
  String getExpression();

}
