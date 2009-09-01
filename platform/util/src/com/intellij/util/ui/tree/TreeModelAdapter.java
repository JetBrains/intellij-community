package com.intellij.util.ui.tree;

import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeModelEvent;

/**
 * @author dyoma
 */
public abstract class TreeModelAdapter implements TreeModelListener {
  public void treeNodesChanged(TreeModelEvent e) {
  }

  public void treeNodesInserted(TreeModelEvent e) {
  }

  public void treeNodesRemoved(TreeModelEvent e) {
  }

  public void treeStructureChanged(TreeModelEvent e) {
  }
}
