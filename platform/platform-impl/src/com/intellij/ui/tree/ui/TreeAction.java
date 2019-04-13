// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.TreeActions;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.List;

abstract class TreeAction extends AbstractAction implements UIResource {
  private static final List<TreeAction> ACTIONS = Arrays.asList(
    new TreeAction(TreeActions.Up.ID) {
      @Override
      void actionPerformed(@NotNull JTree tree, @Nullable TreePath path) {
        int row = tree.getRowForPath(path);
        if (path == null || row < 0) {
          scrollAndSetSelection(tree, 0);
        }
        else {
          if (row == 0 && isCycleScrollingAllowed()) row = tree.getRowCount();
          row--; // NB!: decrease row after checking for cycle scrolling
          scrollAndSetSelection(tree, row);
        }
      }
    },
    new TreeAction(TreeActions.Down.ID) {
      @Override
      void actionPerformed(@NotNull JTree tree, @Nullable TreePath path) {
        int row = tree.getRowForPath(path);
        if (path == null || row < 0) {
          scrollAndSetSelection(tree, 0);
        }
        else {
          row++; // NB!: increase row before checking for cycle scrolling
          if (isCycleScrollingAllowed() && row == tree.getRowCount()) row = 0;
          scrollAndSetSelection(tree, row);
        }
      }
    },
    new TreeAction(TreeActions.Left.ID) {
      @Override
      void actionPerformed(@NotNull JTree tree, @Nullable TreePath path) {
        int row = tree.getRowForPath(path);
        if (path == null || row < 0) {
          scrollAndSetSelection(tree, 0);
        }
        else if (tree.isExpanded(path)) {
          tree.collapsePath(path);
        }
        else {
          TreePath parent = path.getParentPath();
          if (parent != null) {
            if (tree.isRootVisible() || null != parent.getParentPath()) {
              scrollAndSetSelection(tree, parent);
            }
            else if (row > 0) {
              scrollAndSetSelection(tree, row - 1);
            }
          }
        }
      }
    },
    new TreeAction(TreeActions.Right.ID) {
      @Override
      void actionPerformed(@NotNull JTree tree, @Nullable TreePath path) {
        int row = tree.getRowForPath(path);
        if (path == null || row < 0) {
          scrollAndSetSelection(tree, 0);
        }
        else if (tree.isExpanded(path) || tree.getModel().isLeaf(path.getLastPathComponent())) {
          scrollAndSetSelection(tree, row + 1);
        }
        else {
          tree.expandPath(path);
        }
      }
    }
  );

  TreeAction(@NotNull String name) {
    super(name);
  }

  abstract void actionPerformed(@NotNull JTree tree, @Nullable TreePath path);

  @Override
  public final void actionPerformed(ActionEvent event) {
    Object source = event.getSource();
    if (source instanceof JTree) {
      JTree tree = (JTree)source;
      actionPerformed(tree, tree.getLeadSelectionPath());
    }
  }

  static void installTo(@NotNull ActionMap map) {
    Object[] keys = map.keys();
    if (keys != null && keys.length != 0) return; // actions are already installed
    for (TreeAction action : ACTIONS) map.put(action.getValue(NAME), action);
  }

  static void scrollAndSetSelection(@NotNull JTree tree, int row) {
    scrollAndSetSelection(tree, tree.getPathForRow(row));
  }

  static void scrollAndSetSelection(@NotNull JTree tree, @Nullable TreePath path) {
    if (path != null && TreeUtil.scrollToVisible(tree, path, false)) tree.setSelectionPath(path);
  }

  static boolean isCycleScrollingAllowed() {
    if (!Registry.is("ide.tree.ui.cyclic.scrolling.allowed")) return false;
    UISettings settings = UISettings.getInstanceOrNull();
    return settings != null && settings.getCycleScrolling();
  }
}
