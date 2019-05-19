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
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import static java.util.Arrays.asList;
import static javax.swing.KeyStroke.getKeyStroke;

abstract class TreeAction extends AbstractAction implements UIResource {
  private static final List<TreeAction> ACTIONS = asList(
    new TreeAction(TreeActions.Home.ID, getKeyStroke(KeyEvent.VK_HOME, 0)) {
      @Override
      void actionPerformed(@NotNull JTree tree, @Nullable TreePath path) {
        scrollAndSetSelection(tree, 0);
      }
    },
    new TreeAction(TreeActions.End.ID, getKeyStroke(KeyEvent.VK_END, 0)) {
      @Override
      void actionPerformed(@NotNull JTree tree, @Nullable TreePath path) {
        scrollAndSetSelection(tree, tree.getRowCount() - 1);
      }
    },
    new TreeAction(TreeActions.Up.ID, getKeyStroke(KeyEvent.VK_UP, 0), getKeyStroke(KeyEvent.VK_KP_UP, 0)) {
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
    new TreeAction(TreeActions.Down.ID, getKeyStroke(KeyEvent.VK_DOWN, 0), getKeyStroke(KeyEvent.VK_KP_DOWN, 0)) {
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
    new TreeAction(TreeActions.Left.ID, getKeyStroke(KeyEvent.VK_LEFT, 0), getKeyStroke(KeyEvent.VK_KP_LEFT, 0)) {
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
    new TreeAction(TreeActions.Right.ID, getKeyStroke(KeyEvent.VK_RIGHT, 0), getKeyStroke(KeyEvent.VK_KP_RIGHT, 0)) {
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
    },
    new TreeAction(TreeActions.PageUp.ID, getKeyStroke(KeyEvent.VK_PAGE_UP, 0)) {
      @Override
      void actionPerformed(@NotNull JTree tree, @Nullable TreePath path) {
        Rectangle bounds = tree.getPathBounds(path);
        if (path == null || bounds == null) {
          scrollAndSetSelection(tree, 0);
        }
        else {
          int height = Math.max(tree.getVisibleRect().height - bounds.height, 1);
          TreePath next = tree.getClosestPathForLocation(bounds.x, bounds.y - height);
          if (next != null && !next.equals(path)) scrollAndSetSelection(tree, next);
        }
      }
    },
    new TreeAction(TreeActions.PageDown.ID, getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0)) {
      @Override
      void actionPerformed(@NotNull JTree tree, @Nullable TreePath path) {
        Rectangle bounds = tree.getPathBounds(path);
        if (path == null || bounds == null) {
          scrollAndSetSelection(tree, tree.getRowCount() - 1);
        }
        else {
          int height = Math.max(tree.getVisibleRect().height - bounds.height, 1);
          TreePath next = tree.getClosestPathForLocation(bounds.x, bounds.y + bounds.height + height);
          if (next != null && !next.equals(path)) scrollAndSetSelection(tree, next);
        }
      }
    }
  );
  private final String name;
  private final List<KeyStroke> keys;

  TreeAction(@NotNull String name, @NotNull KeyStroke... keys) {
    this.name = name;
    this.keys = asList(keys);
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
    for (TreeAction action : ACTIONS) map.put(action.name, action);
  }

  static void installTo(@NotNull InputMap map) {
    Object[] keys = map.keys();
    if (keys != null && keys.length != 0) return; // keys for actions are already installed
    for (TreeAction action : ACTIONS) for (KeyStroke key : action.keys) map.put(key, action.name);
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
