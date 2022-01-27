// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.function.Function;
import java.util.function.Predicate;

public final class TreeTestUtil {
  private final JTree tree;
  private boolean selection;
  private TreeVisitor visitor = path -> TreeVisitor.Action.CONTINUE;
  private Predicate<? super TreePath> filter = path -> true;
  private Function<Object, String> converter = node -> PlatformTestUtil.toString(node, null);

  public TreeTestUtil(@NotNull JTree tree) {
    assertTreeUI(tree);
    this.tree = tree;
  }

  public TreeTestUtil withSelection() {
    return setSelection(true);
  }

  public TreeTestUtil setSelection(boolean selection) {
    this.selection = selection;
    return this;
  }

  public TreeTestUtil setVisitor(@NotNull TreeVisitor visitor) {
    this.visitor = visitor;
    return this;
  }

  public TreeTestUtil setFilter(@NotNull Predicate<? super TreePath> filter) {
    this.filter = filter;
    return this;
  }

  public TreeTestUtil setConverter(@NotNull Function<Object, String> converter) {
    this.converter = converter;
    return this;
  }

  public TreeTestUtil expandAll() {
    PlatformTestUtil.expandAll(tree);
    return this;
  }

  public void assertStructure(@NonNls String expected) {
    PlatformTestUtil.waitWhileBusy(tree);
    Assert.assertEquals(expected, toString());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    TreeUtil.visitVisibleRows(tree, path -> {
      TreeVisitor.Action action = visitor.visit(path);
      if (filter.test(path)) {
        int count = path.getPathCount();
        for (int i = 1; i < count; i++) sb.append(' ');
        Object component = path.getLastPathComponent();
        if (!tree.getModel().isLeaf(component)) sb.append(tree.isExpanded(path) ? '-' : '+');
        boolean selected = selection && tree.isPathSelected(path);
        if (selected) sb.append('[');
        sb.append(converter.apply(TreeUtil.getUserObject(component)));
        if (selected) sb.append(']');
        sb.append('\n');
      }
      return action;
    });
    return sb.toString();
  }


  @NotNull
  public static DefaultMutableTreeNode node(@NotNull Object object, Object... children) {
    if (object instanceof DefaultMutableTreeNode && ArrayUtil.isEmpty(children)) return (DefaultMutableTreeNode)object;
    if (object instanceof TreeNode) throw new IllegalArgumentException("do not use a tree node as a node content");
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(object);
    for (Object child : children) node.add(node(child));
    return node;
  }


  public static void assertTreeUI(@NotNull JTree tree) {
    Assert.assertTrue(tree.getUI() instanceof BasicTreeUI);
  }
}
