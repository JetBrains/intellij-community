package com.intellij.ui;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public abstract class BasicTreeModel implements TreeModel {
  public boolean isLeaf(Object node) {
      return getChildCount(node) == 0;
  }

  public void valueForPathChanged(TreePath path, Object newValue) {
  }

  public void addTreeModelListener(TreeModelListener l) {
  }

  public void removeTreeModelListener(TreeModelListener l) {
  }

  public static int getIndex(Object[] children, Object child) {
    for (int i = 0; i < children.length; i++) {
      Object objectInstance = children[i];
      if (objectInstance == child)
        return i;
    }
    return -1;
  }
}
