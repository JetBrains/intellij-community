// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.ui.TreeActions;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.UIResource;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.function.Consumer;

import static java.awt.event.KeyEvent.*;
import static java.util.Arrays.asList;
import static javax.swing.KeyStroke.getKeyStroke;
import static javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;

final class TreeAction extends AbstractAction implements UIResource {
  private enum MoveType {ChangeLead, ChangeSelection, ExtendSelection}

  private static final List<TreeAction> ACTIONS = asList(
    new TreeAction(TreeAction::selectFirst, TreeActions.Home.ID, getKeyStroke(VK_HOME, 0)),
    new TreeAction(TreeAction::selectFirstChangeLead, "selectFirstChangeLead"),
    new TreeAction(TreeAction::selectFirstExtendSelection, TreeActions.ShiftHome.ID),

    new TreeAction(TreeAction::selectLast, TreeActions.End.ID, getKeyStroke(VK_END, 0)),
    new TreeAction(TreeAction::selectLastChangeLead, "selectLastChangeLead"),
    new TreeAction(TreeAction::selectLastExtendSelection, TreeActions.ShiftEnd.ID),

    new TreeAction(TreeAction::selectPrevious, TreeActions.Up.ID, getKeyStroke(VK_UP, 0), getKeyStroke(VK_KP_UP, 0)),
    new TreeAction(TreeAction::selectPreviousChangeLead, "selectPreviousChangeLead"),
    new TreeAction(TreeAction::selectPreviousExtendSelection, TreeActions.ShiftUp.ID),

    new TreeAction(TreeAction::selectNext, TreeActions.Down.ID, getKeyStroke(VK_DOWN, 0), getKeyStroke(VK_KP_DOWN, 0)),
    new TreeAction(TreeAction::selectNextChangeLead, "selectNextChangeLead"),
    new TreeAction(TreeAction::selectNextExtendSelection, TreeActions.ShiftDown.ID),

    new TreeAction(TreeAction::selectParentNoCollapse, TreeActions.SelectParent.ID),
    new TreeAction(TreeAction::selectParent, TreeActions.Left.ID, getKeyStroke(VK_LEFT, 0), getKeyStroke(VK_KP_LEFT, 0)),
    // new TreeAction(TreeAction::selectParentChangeLead, "selectParentChangeLead"),
    // new TreeAction(TreeAction::selectParentExtendSelection, TreeActions.ShiftLeft.ID),

    new TreeAction(TreeAction::selectChild, TreeActions.Right.ID, getKeyStroke(VK_RIGHT, 0), getKeyStroke(VK_KP_RIGHT, 0)),
    // new TreeAction(TreeAction::selectChildChangeLead, "selectChildChangeLead"),
    // new TreeAction(TreeAction::selectChildExtendSelection, TreeActions.ShiftRight.ID),

    new TreeAction(TreeAction::scrollUpChangeSelection, TreeActions.PageUp.ID, getKeyStroke(VK_PAGE_UP, 0)),
    new TreeAction(TreeAction::scrollUpChangeLead, "scrollUpChangeLead"),
    new TreeAction(TreeAction::scrollUpExtendSelection, TreeActions.ShiftPageUp.ID),

    new TreeAction(TreeAction::scrollDownChangeSelection, TreeActions.PageDown.ID, getKeyStroke(VK_PAGE_DOWN, 0)),
    new TreeAction(TreeAction::scrollDownChangeLead, "scrollDownChangeLead"),
    new TreeAction(TreeAction::scrollDownExtendSelection, TreeActions.ShiftPageDown.ID),

    new TreeAction(TreeAction::selectNextSibling, TreeActions.NextSibling.ID),
    new TreeAction(TreeAction::selectPreviousSibling, TreeActions.PreviousSibling.ID)
  );
  private final String name;
  private final @NotNull Consumer<? super JTree> action;
  private final List<KeyStroke> keys;

  private TreeAction(@NotNull Consumer<? super JTree> action, @NotNull @NonNls String name, KeyStroke @NotNull ... keys) {
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

  private static boolean isCycleScrollingAllowed(@NotNull MoveType type) {
    return type != MoveType.ExtendSelection && TreeUtil.isCyclicScrollingAllowed();
  }

  private static boolean isLeaf(@NotNull JTree tree, @NotNull TreePath path) {
    return tree.getModel().isLeaf(path.getLastPathComponent()); // TODO:malenkov: via DefaultTreeUI
  }

  private static void lineDown(@NotNull MoveType type, @NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    int row = tree.getRowForPath(lead);
    if (lead == null || row < 0) {
      selectFirstExceptSeparator(type, tree);
    }
    else {
      row = findRowExceptSeparator(tree, row, false); // NB!: increase row before checking for cycle scrolling
      if (isCycleScrollingAllowed(type) && row == tree.getRowCount()) row = 0;
      selectExceptSeparator(type, tree, row);
    }
  }

  private static void lineUp(@NotNull MoveType type, @NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    int row = tree.getRowForPath(lead);
    if (lead == null || row < 0) {
      selectFirstExceptSeparator(type, tree);
    }
    else {
      if (row == 0 && isCycleScrollingAllowed(type)) row = tree.getRowCount();
      row = findRowExceptSeparator(tree, row, true); // NB!: decrease row after checking for cycle scrolling
      selectExceptSeparator(type, tree, row);
    }
  }

  private static int findRowExceptSeparator(@NotNull JTree tree, int row, boolean up) {
    TreePath curPath;
    int curRow = row;
    do {
      if (up) {
        curRow--;
      }
      else {
        curRow++;
      }
      curPath = tree.getPathForRow(curRow);
    }
    while (curPath != null && DefaultTreeUI.isSeparator(curPath));

    return curRow;
  }

  private static void pageDown(@NotNull MoveType type, @NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    Rectangle bounds = tree.getPathBounds(lead);
    if (lead == null || bounds == null) {
      selectLast(type, tree);
    }
    else {
      int height = Math.max(tree.getVisibleRect().height - bounds.height * 4, 1);
      TreePath next = tree.getClosestPathForLocation(bounds.x, bounds.y + bounds.height + height);
      if (next != null && !next.equals(lead)) select(type, tree, next);
    }
  }

  private static void pageUp(@NotNull MoveType type, @NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    Rectangle bounds = tree.getPathBounds(lead);
    if (lead == null || bounds == null) {
      selectFirst(type, tree);
    }
    else {
      int height = Math.max(tree.getVisibleRect().height - bounds.height * 4, 1);
      TreePath next = tree.getClosestPathForLocation(bounds.x, bounds.y - height);
      if (next != null && !next.equals(lead)) select(type, tree, next);
    }
  }

  private static void select(@NotNull MoveType type, @NotNull JTree tree, int row) {
    select(type, tree, tree.getPathForRow(row), row);
  }

  private static void select(@NotNull MoveType type, @NotNull JTree tree, @NotNull TreePath path) {
    select(type, tree, path, tree.getRowForPath(path));
  }

  private static void select(@NotNull MoveType type, @NotNull JTree tree, @Nullable TreePath path, int row) {
    if (path == null || row < 0) return;
    if (type == MoveType.ExtendSelection) {
      TreePath anchor = tree.getAnchorSelectionPath();
      int anchorRow = anchor == null ? -1 : tree.getRowForPath(anchor);
      if (anchorRow < 0) {
        tree.setSelectionPath(path);
      }
      else {
        tree.setSelectionInterval(row, anchorRow);
        tree.setAnchorSelectionPath(anchor);
        tree.setLeadSelectionPath(path);
      }
    }
    else if (type == MoveType.ChangeLead && DISCONTIGUOUS_TREE_SELECTION == tree.getSelectionModel().getSelectionMode()) {
      tree.setLeadSelectionPath(path);
    }
    else {
      tree.setSelectionPath(path);
    }
    TreeUtil.scrollToVisible(tree, path, false);
  }

  private static void selectChild(@NotNull MoveType type, @NotNull JTree tree) {
    TreePath lead = tree.getLeadSelectionPath();
    int row = tree.getRowForPath(lead);
    if (lead == null || row < 0) {
      selectFirst(type, tree);
    }
    else if (tree.isExpanded(lead) || isLeaf(tree, lead)) {
      TreePath path = tree.getPathForRow(row + 1);
      if (!TreeUtil.isLoadingPath(path)) select(type, tree, path, row + 1);
    }
    else {
      tree.expandPath(lead);
    }
  }

  private static void selectFirst(@NotNull MoveType type, @NotNull JTree tree) {
    select(type, tree, 0);
  }

  private static void selectFirstExceptSeparator(@NotNull MoveType type, @NotNull JTree tree) {
    selectExceptSeparator(type, tree, 0);
  }

  private static void selectExceptSeparator(@NotNull MoveType type, @NotNull JTree tree, int row) {
    TreePath path = tree.getPathForRow(row);
    if (!DefaultTreeUI.isSeparator(path)) {
      select(type, tree, path, row);
    }
  }

  private static void selectLast(@NotNull MoveType type, @NotNull JTree tree) {
    select(type, tree, tree.getRowCount() - 1);
  }

  private static void selectParent(@NotNull MoveType type, @NotNull JTree tree, boolean canCollapse) {
    TreePath lead = tree.getLeadSelectionPath();
    int row = tree.getRowForPath(lead);
    if (lead == null || row < 0) {
      selectFirst(type, tree);
    }
    else if (canCollapse && tree.isExpanded(lead)) {
      tree.collapsePath(lead);
    }
    else {
      TreePath parent = lead.getParentPath();
      if (parent != null) {
        if (tree.isRootVisible() || null != parent.getParentPath()) {
          select(type, tree, parent);
        }
        else if (row > 0) {
          TreePath path = TreeUtil.previousVisiblePath(tree, row, false, tree::isExpanded);
          select(type, tree, path != null ? path : tree.getPathForRow(0), path == null ? 0 : tree.getRowForPath(path));
        }
      }
    }
  }

  private static void selectNextSibling(@NotNull JTree tree) {
    TreePath sibling = TreeUtil.nextVisibleSibling(tree, tree.getLeadSelectionPath());
    if (sibling == null) return; // next sibling is not found
    tree.setSelectionPath(sibling);
    TreeUtil.scrollToVisible(tree, sibling, false);
  }

  private static void selectPreviousSibling(@NotNull JTree tree) {
    TreePath sibling = TreeUtil.previousVisibleSibling(tree, tree.getLeadSelectionPath());
    if (sibling == null) return; // previous sibling is not found
    tree.setSelectionPath(sibling);
    TreeUtil.scrollToVisible(tree, sibling, false);
  }

  // NB!: the following method names correspond Tree.focusInputMap in BasicLookAndFeel and Actions in BasicTreeUI

  @SuppressWarnings("unused") // TODO:malenkov: implement addToSelection
  private static void addToSelection(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement cancel
  private static void cancel(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement clearSelection
  private static void clearSelection(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement collapse
  private static void collapse(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement expand
  private static void expand(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement extendTo
  private static void extendTo(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement moveSelectionTo
  private static void moveSelectionTo(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement moveSelectionToParent
  private static void moveSelectionToParent(@NotNull JTree tree) {
  }

  private static void scrollDownChangeLead(@NotNull JTree tree) {
    pageDown(MoveType.ChangeLead, tree);
  }

  private static void scrollDownChangeSelection(@NotNull JTree tree) {
    pageDown(MoveType.ChangeSelection, tree);
  }

  private static void scrollDownExtendSelection(@NotNull JTree tree) {
    pageDown(MoveType.ExtendSelection, tree);
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement scrollLeft
  private static void scrollLeft(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement scrollLeftChangeLead
  private static void scrollLeftChangeLead(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement scrollLeftExtendSelection
  private static void scrollLeftExtendSelection(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement scrollRight
  private static void scrollRight(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement scrollRightChangeLead
  private static void scrollRightChangeLead(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement scrollRightExtendSelection
  private static void scrollRightExtendSelection(@NotNull JTree tree) {
  }

  private static void scrollUpChangeLead(@NotNull JTree tree) {
    pageUp(MoveType.ChangeLead, tree);
  }

  private static void scrollUpChangeSelection(@NotNull JTree tree) {
    pageUp(MoveType.ChangeSelection, tree);
  }

  private static void scrollUpExtendSelection(@NotNull JTree tree) {
    pageUp(MoveType.ExtendSelection, tree);
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement selectAll
  private static void selectAll(@NotNull JTree tree) {
  }

  private static void selectChild(@NotNull JTree tree) {
    selectChild(MoveType.ChangeSelection, tree);
  }

  @SuppressWarnings("unused")
  private static void selectChildChangeLead(@NotNull JTree tree) {
    selectChild(MoveType.ChangeLead, tree);
  }

  @SuppressWarnings("unused") // because inconvenient
  private static void selectChildExtendSelection(@NotNull JTree tree) {
    selectChild(MoveType.ExtendSelection, tree);
  }

  private static void selectFirst(@NotNull JTree tree) {
    selectFirst(MoveType.ChangeSelection, tree);
  }

  private static void selectFirstChangeLead(@NotNull JTree tree) {
    selectFirst(MoveType.ChangeLead, tree);
  }

  private static void selectFirstExtendSelection(@NotNull JTree tree) {
    selectFirst(MoveType.ExtendSelection, tree);
  }

  private static void selectLast(@NotNull JTree tree) {
    selectLast(MoveType.ChangeSelection, tree);
  }

  private static void selectLastChangeLead(@NotNull JTree tree) {
    selectLast(MoveType.ChangeLead, tree);
  }

  private static void selectLastExtendSelection(@NotNull JTree tree) {
    selectLast(MoveType.ExtendSelection, tree);
  }

  private static void selectNext(@NotNull JTree tree) {
    lineDown(MoveType.ChangeSelection, tree);
  }

  private static void selectNextChangeLead(@NotNull JTree tree) {
    lineDown(MoveType.ChangeLead, tree);
  }

  private static void selectNextExtendSelection(@NotNull JTree tree) {
    lineDown(MoveType.ExtendSelection, tree);
  }

  private static void selectParent(@NotNull JTree tree) {
    selectParent(MoveType.ChangeSelection, tree, true);
  }

  private static void selectParentNoCollapse(@NotNull JTree tree) {
    selectParent(MoveType.ChangeSelection, tree, false);
  }

  @SuppressWarnings("unused")
  private static void selectParentChangeLead(@NotNull JTree tree) {
    selectParent(MoveType.ChangeLead, tree, false);
  }

  @SuppressWarnings("unused") // because inconvenient
  private static void selectParentExtendSelection(@NotNull JTree tree) {
    selectParent(MoveType.ExtendSelection, tree, false);
  }

  private static void selectPrevious(@NotNull JTree tree) {
    lineUp(MoveType.ChangeSelection, tree);
  }

  private static void selectPreviousChangeLead(@NotNull JTree tree) {
    lineUp(MoveType.ChangeLead, tree);
  }

  private static void selectPreviousExtendSelection(@NotNull JTree tree) {
    lineUp(MoveType.ExtendSelection, tree);
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement startEditing
  private static void startEditing(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement toggle
  private static void toggle(@NotNull JTree tree) {
  }

  @SuppressWarnings("unused") // TODO:malenkov: implement toggleAndAnchor
  private static void toggleAndAnchor(@NotNull JTree tree) {
  }
}
