// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public final class TreeExpandCollapse {
  public static void collapse(JTree tree) {
    TreePath selectionPath = tree.getSelectionPath();
    tree.collapsePath(selectionPath);
  }

  public static void expand(JTree tree) {
    TreePath selectionPath = tree.getSelectionPath();
    tree.expandPath(selectionPath);
  }

  public static void expandAll(JTree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null) {
      paths = new TreePath[] { new TreePath(tree.getModel().getRoot()) };
    }
    for (TreePath path : paths) {
      new ExpandContext(300, 10).expand(tree, path);
    }
  }

  private static class ExpandContext {
    private final int myLevelsLeft;
    private int myExpansionLimit;

    ExpandContext(int expansionLimit, int levelsLeft) {
      myExpansionLimit = expansionLimit;
      myLevelsLeft = levelsLeft;
    }

    public int expand(JTree tree, TreePath path) {
      if (myLevelsLeft == 0) return myExpansionLimit;
      TreeModel model = tree.getModel();
      Object node = path.getLastPathComponent();
      int levelDecrement = 0;
      if (!tree.isExpanded(path) && !model.isLeaf(node)) {
        tree.expandPath(path);
        levelDecrement = 1;
        myExpansionLimit--;
      }
      for (int i = 0; i < model.getChildCount(node); i++) {
        Object child = model.getChild(node, i);
        if (model.isLeaf(child)) continue;
        ExpandContext childContext = new ExpandContext(myExpansionLimit, myLevelsLeft - levelDecrement);
        myExpansionLimit = childContext.expand(tree, path.pathByAddingChild(child));
        if (myExpansionLimit <= 0) return 0;
      }
      return myExpansionLimit;
    }
  }
}
