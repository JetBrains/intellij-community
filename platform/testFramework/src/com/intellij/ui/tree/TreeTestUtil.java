// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.function.Function;
import java.util.function.Predicate;

public class TreeTestUtil {
  public static final TreeVisitor VISIT_ALL = path -> TreeVisitor.Action.CONTINUE;
  public static final Predicate<TreePath> APPEND_ALL = path -> true;
  public static final Function<Object, String> TO_STRING = node -> PlatformTestUtil.toString(node, null);


  @NotNull
  public static String toString(@NotNull JTree tree) {
    return toString(tree, VISIT_ALL, APPEND_ALL, false, TO_STRING);
  }

  @NotNull
  public static String toString(@NotNull JTree tree, boolean showSelection) {
    return toString(tree, VISIT_ALL, APPEND_ALL, showSelection, TO_STRING);
  }

  @NotNull
  public static String toString(@NotNull JTree tree, @NotNull Predicate<? super TreePath> append, boolean showSelection) {
    return toString(tree, VISIT_ALL, append, showSelection, TO_STRING);
  }

  @NotNull
  public static String toString(@NotNull JTree tree, boolean showSelection, @NotNull Function<Object, String> toString) {
    return toString(tree, VISIT_ALL, APPEND_ALL, showSelection, toString);
  }

  @NotNull
  public static String toString(@NotNull JTree tree,
                                @NotNull TreeVisitor visitor,
                                @NotNull Predicate<? super TreePath> append,
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


  /**
   * @param tree     a tree, which visible rows are used to create a string representation
   * @param expected expected string representation of the given tree
   */
  public static void assertStructure(@NotNull JTree tree, @NonNls String expected) {
    PlatformTestUtil.waitWhileBusy(tree);
    Assert.assertEquals(expected, toString(tree));
  }

  /**
   * @param tree     a tree, which visible rows are used to create a string representation
   * @param expected expected string representation of the given tree
   */
  public static void assertStructureWithSelection(@NotNull JTree tree, @NonNls String expected) {
    PlatformTestUtil.waitWhileBusy(tree);
    Assert.assertEquals(expected, toString(tree, true));
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
