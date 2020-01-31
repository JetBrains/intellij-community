// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.util.List;
import java.util.function.Consumer;

import static java.awt.event.KeyEvent.*;
import static java.util.Arrays.asList;
import static javax.swing.KeyStroke.getKeyStroke;

final class TreeAction extends AbstractAction implements UIResource {
  private static final List<TreeAction> ACTIONS = asList(
    new TreeAction(TreeActions.Home.ID, TreeAction::selectFirst, getKeyStroke(VK_HOME, 0)),
    new TreeAction(TreeActions.End.ID, TreeAction::selectLast, getKeyStroke(VK_END, 0)),
    new TreeAction(TreeActions.Up.ID, TreeAction::selectPrevious, getKeyStroke(VK_UP, 0), getKeyStroke(VK_KP_UP, 0)),
    new TreeAction(TreeActions.Down.ID, TreeAction::selectNext, getKeyStroke(VK_DOWN, 0), getKeyStroke(VK_KP_DOWN, 0)),
    new TreeAction(TreeActions.Left.ID, TreeAction::selectParent, getKeyStroke(VK_LEFT, 0), getKeyStroke(VK_KP_LEFT, 0)),
    new TreeAction(TreeActions.Right.ID, TreeAction::selectChild, getKeyStroke(VK_RIGHT, 0), getKeyStroke(VK_KP_RIGHT, 0)),
    new TreeAction(TreeActions.PageUp.ID, TreeAction::scrollUpChangeSelection, getKeyStroke(VK_PAGE_UP, 0)),
    new TreeAction(TreeActions.PageDown.ID, TreeAction::scrollDownChangeSelection, getKeyStroke(VK_PAGE_DOWN, 0))
  );
  private final String name;
  private final Consumer<JTree> action;
  private final List<KeyStroke> keys;

  private TreeAction(@NotNull String name, @NotNull Consumer<JTree> action, KeyStroke @NotNull ... keys) {
    this.name = name;
    this.action = action;
    this.keys = asList(keys);
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    Object source = event.getSource();
    if (source instanceof JTree) action.accept((JTree)source);
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

  private static void scrollAndSetSelection(@NotNull JTree tree, int row) {
    scrollAndSetSelection(tree, tree.getPathForRow(row));
  }

  private static void scrollAndSetSelection(@NotNull JTree tree, @Nullable TreePath path) {
    if (path != null && TreeUtil.scrollToVisible(tree, path, false)) tree.setSelectionPath(path);
  }

  private static boolean isCycleScrollingAllowed() {
    if (!Registry.is("ide.tree.ui.cyclic.scrolling.allowed")) return false;
    UISettings settings = UISettings.getInstanceOrNull();
    return settings != null && settings.getCycleScrolling();
  }


  // NB!: the following method names correspond Tree.focusInputMap in BasicLookAndFeel

  @SuppressWarnings("unused") // TODO:malenkov
  private static void addToSelection(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void clearSelection(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void extendTo(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void moveSelectionTo(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void scrollDownChangeLead(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  private static void scrollDownChangeSelection(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    Rectangle bounds = tree.getPathBounds(lead);
    if (lead == null || bounds == null) {
      scrollAndSetSelection(tree, tree.getRowCount() - 1);
    }
    else {
      int height = Math.max(tree.getVisibleRect().height - bounds.height, 1);
      TreePath next = tree.getClosestPathForLocation(bounds.x, bounds.y + bounds.height + height);
      if (next != null && !next.equals(lead)) scrollAndSetSelection(tree, next);
    }
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void scrollDownExtendSelection(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void scrollLeft(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void scrollRight(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void scrollUpChangeLead(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  private static void scrollUpChangeSelection(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    Rectangle bounds = tree.getPathBounds(lead);
    if (lead == null || bounds == null) {
      scrollAndSetSelection(tree, 0);
    }
    else {
      int height = Math.max(tree.getVisibleRect().height - bounds.height, 1);
      TreePath next = tree.getClosestPathForLocation(bounds.x, bounds.y - height);
      if (next != null && !next.equals(lead)) scrollAndSetSelection(tree, next);
    }
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void scrollUpExtendSelection(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void selectAll(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  private static void selectChild(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    int row = tree.getRowForPath(lead);
    if (lead == null || row < 0) {
      scrollAndSetSelection(tree, 0);
    }
    else if (tree.isExpanded(lead) || tree.getModel().isLeaf(lead.getLastPathComponent())) {
      scrollAndSetSelection(tree, row + 1);
    }
    else {
      tree.expandPath(lead);
    }
  }

  private static void selectFirst(@NotNull JTree tree) {
    scrollAndSetSelection(tree, 0);
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void selectFirstChangeLead(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void selectFirstExtendSelection(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  private static void selectLast(@NotNull JTree tree) {
    scrollAndSetSelection(tree, tree.getRowCount() - 1);
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void selectLastChangeLead(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void selectLastExtendSelection(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  private static void selectNext(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    int row = tree.getRowForPath(lead);
    if (lead == null || row < 0) {
      scrollAndSetSelection(tree, 0);
    }
    else {
      row++; // NB!: increase row before checking for cycle scrolling
      if (isCycleScrollingAllowed() && row == tree.getRowCount()) row = 0;
      scrollAndSetSelection(tree, row);
    }
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void selectNextChangeLead(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void selectNextExtendSelection(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  private static void selectParent(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    int row = tree.getRowForPath(lead);
    if (lead == null || row < 0) {
      scrollAndSetSelection(tree, 0);
    }
    else if (tree.isExpanded(lead)) {
      tree.collapsePath(lead);
    }
    else {
      TreePath parent = lead.getParentPath();
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

  private static void selectPrevious(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    int row = tree.getRowForPath(lead);
    if (lead == null || row < 0) {
      scrollAndSetSelection(tree, 0);
    }
    else {
      if (row == 0 && isCycleScrollingAllowed()) row = tree.getRowCount();
      row--; // NB!: decrease row after checking for cycle scrolling
      scrollAndSetSelection(tree, row);
    }
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void selectPreviousChangeLead(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void selectPreviousExtendSelection(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void startEditing(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }

  @SuppressWarnings("unused") // TODO:malenkov
  private static void toggleAndAnchor(@NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
  }
}
