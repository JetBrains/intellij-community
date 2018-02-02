// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.ui.Queryable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.function.Function;
import java.util.function.Predicate;

public class TreeTestUtil {
  public static final TreeVisitor VISIT_ALL = path -> TreeVisitor.Action.CONTINUE;
  public static final Predicate<TreePath> APPEND_ALL = path -> true;
  public static final Function<Object, String> TO_STRING = getToString(null);

  @NotNull
  public static Function<Object, String> getToString(@Nullable Queryable.PrintInfo info) {
    return object -> {
      if (object instanceof AbstractTreeNode) {
        AbstractTreeNode node = (AbstractTreeNode)object;
        return node.toTestString(info);
      }
      return String.valueOf(object);
    };
  }

  @NotNull
  public static String toString(@NotNull JTree tree) {
    return toString(tree, VISIT_ALL, APPEND_ALL, false, TO_STRING);
  }

  @NotNull
  public static String toString(@NotNull JTree tree, boolean showSelection) {
    return toString(tree, VISIT_ALL, APPEND_ALL, showSelection, TO_STRING);
  }

  @NotNull
  public static String toString(@NotNull JTree tree, @NotNull Predicate<TreePath> append, boolean showSelection) {
    return toString(tree, VISIT_ALL, append, showSelection, TO_STRING);
  }

  @NotNull
  public static String toString(@NotNull JTree tree, boolean showSelection, @NotNull Function<Object, String> toString) {
    return toString(tree, VISIT_ALL, APPEND_ALL, showSelection, toString);
  }

  @NotNull
  public static String toString(@NotNull JTree tree,
                                @NotNull TreeVisitor visitor,
                                @NotNull Predicate<TreePath> append,
                                boolean showSelection,
                                @NotNull Function<Object, String> toString) {
    StringBuilder sb = new StringBuilder();
    TreeUtil.visitVisibleRows(tree, path -> {
      TreeVisitor.Action action = visitor.visit(path);
      if (append.test(path)) {
        int count = path.getPathCount();
        for (int i = 1; i < count; i++) sb.append(' ');
        Object component = path.getLastPathComponent();
        if (!tree.getModel().isLeaf(component)) sb.append(tree.isExpanded(path) ? '-' : '+');
        boolean selected = showSelection && tree.isPathSelected(path);
        if (selected) sb.append('[');
        sb.append(toString.apply(TreeUtil.getUserObject(component)));
        if (selected) sb.append(']');
        sb.append('\n');
      }
      return action;
    });
    return sb.toString();
  }

  public static boolean isProcessing(@NotNull JTree tree) {
    TreeModel model = tree.getModel();
    if (model instanceof AsyncTreeModel) {
      AsyncTreeModel async = (AsyncTreeModel)model;
      return async.isProcessing();
    }
    return false;
  }

  @NotNull
  public static DefaultMutableTreeNode node(@NotNull Object object, Object... children) {
    if (object instanceof DefaultMutableTreeNode && ArrayUtil.isEmpty(children)) return (DefaultMutableTreeNode)object;
    if (object instanceof TreeNode) throw new IllegalArgumentException("do not use a tree node as a node content");
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(object);
    for (Object child : children) node.add(node(child));
    return node;
  }
}
