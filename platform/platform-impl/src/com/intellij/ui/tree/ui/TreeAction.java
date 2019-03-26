// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;

abstract class TreeAction extends AbstractAction implements UIResource {
  static final TreeAction SELECT_CHILD = new TreeAction("selectChild") {
    @Override
    void actionPerformed(@NotNull JTree tree, @NotNull TreePath path) {
      if (tree.isExpanded(path) || tree.getModel().isLeaf(path.getLastPathComponent())) {
        int row = tree.getRowForPath(path);
        path = row < 0 ? null : tree.getPathForRow(row + 1);
        if (path != null) {
          tree.setSelectionPath(path);
          tree.scrollPathToVisible(path);
        }
      }
      else {
        tree.expandPath(path);
      }
    }
  };
  static final TreeAction SELECT_PARENT = new TreeAction("selectParent") {
    @Override
    void actionPerformed(@NotNull JTree tree, @NotNull TreePath path) {
      if (tree.isExpanded(path)) {
        tree.collapsePath(path);
      }
      else {
        TreePath parent = path.getParentPath();
        if (parent != null) {
          if (!tree.isRootVisible() && null == parent.getParentPath()) {
            int row = tree.getRowForPath(path);
            parent = row < 1 ? null : tree.getPathForRow(row - 1);
          }
          if (parent != null) {
            tree.setSelectionPath(parent);
            tree.scrollPathToVisible(parent);
          }
        }
      }
    }
  };

  private TreeAction(@NotNull String name) {
    super(name);
  }

  final void putInto(@NotNull ActionMap map) {
    map.put(getValue(NAME), this);
  }

  abstract void actionPerformed(@NotNull JTree tree, @NotNull TreePath path);

  @Override
  public final void actionPerformed(ActionEvent event) {
    Object source = event.getSource();
    if (source instanceof JTree) {
      JTree tree = (JTree)source;
      TreePath path = tree.getLeadSelectionPath();
      if (path != null) {
        actionPerformed(tree, path);
      }
    }
  }
}
