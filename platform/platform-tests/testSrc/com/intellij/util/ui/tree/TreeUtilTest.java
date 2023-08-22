// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import com.intellij.ui.TreeExpandCollapse;
import com.intellij.ui.tree.TreeTestUtil;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ExceptionUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeUtilTest extends TestCase {
  public void testFindNodeWithObject() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel model = new DefaultTreeModel(root);
    DefaultMutableTreeNode child1 = new DefaultMutableTreeNode("1");
    model.insertNodeInto(child1, root, 0);
    model.insertNodeInto(new DefaultMutableTreeNode("2"), root, 1);
    assertSame(child1, TreeUtil.findNodeWithObject("1", model, root));
    assertNull(TreeUtil.findNodeWithObject("3", model, root));
  }

  public void testRemoveSelected() {
    waitForTestOnEDT(TreeUtilTest::implRemoveSelected);
  }

  private static void implRemoveSelected() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    DefaultMutableTreeNode child1 = new DefaultMutableTreeNode("1");
    model.insertNodeInto(child1, root, 0);
    DefaultMutableTreeNode child2 = new DefaultMutableTreeNode("2");
    model.insertNodeInto(child2, root, 1);
    DefaultMutableTreeNode child11 = new DefaultMutableTreeNode("11");
    model.insertNodeInto(child11, child1, 0);
    JTree tree = new Tree(model);
    TreeTestUtil.assertTreeUI(tree);
    TreeExpandCollapse.expandAll(tree);
    tree.clearSelection();
    TreeUtil.removeSelected(tree);
    assertEquals(2, model.getChildCount(root));
    assertEquals(1, model.getChildCount(child1));
    tree.setSelectionPath(TreeUtil.getPathFromRoot(child11));
    TreeUtil.removeSelected(tree);
    assertSame(child1, tree.getSelectionPath().getLastPathComponent());
    TreeUtil.removeSelected(tree);
    assertSame(child2, tree.getSelectionPath().getLastPathComponent());
    tree.setSelectionPath(new TreePath(root));
    assertEquals(1, model.getChildCount(root));
    TreeUtil.removeSelected(tree);
    assertSame(root, model.getRoot());
    assertEquals(1, model.getChildCount(root));
  }

  public void testMultiLevelRemove() {
    waitForTestOnEDT(TreeUtilTest::implMultiLevelRemove);
  }

  private static void implMultiLevelRemove() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root) {
        @Override
        public void removeNodeFromParent(MutableTreeNode mutableTreeNode) {
          super.removeNodeFromParent((MutableTreeNode) mutableTreeNode.getParent());
        }
      };
    DefaultMutableTreeNode node1 = new DefaultMutableTreeNode("1");
    model.insertNodeInto(node1, root, 0);
    DefaultMutableTreeNode node2 = new DefaultMutableTreeNode("2");
    model.insertNodeInto(node2, node1, 0);
    JTree tree = new Tree(model);
    TreeTestUtil.assertTreeUI(tree);
    TreeExpandCollapse.expandAll(tree);
    tree.setSelectionPath(TreeUtil.getPathFromRoot(node2));
    TreeUtil.removeSelected(tree);
    assertEquals(0, root.getChildCount());
    assertEquals(root, tree.getSelectionPath().getLastPathComponent());
  }

  public void testRemoveLast() {
    waitForTestOnEDT(TreeUtilTest::implRemoveLast);
  }

  private static void implRemoveLast() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    model.insertNodeInto(new DefaultMutableTreeNode("1"), root, 0);
    DefaultMutableTreeNode middle = new DefaultMutableTreeNode("2");
    model.insertNodeInto(middle, root, 1);
    DefaultMutableTreeNode last = new DefaultMutableTreeNode("3");
    model.insertNodeInto(last, root, 2);
    JTree tree = new Tree(model);
    TreeTestUtil.assertTreeUI(tree);
    tree.setSelectionPath(TreeUtil.getPathFromRoot(last));
    TreeUtil.removeSelected(tree);
    assertSame(middle, tree.getSelectionPath().getLastPathComponent());
  }

  public void testSorting() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultMutableTreeNode node2 = new DefaultMutableTreeNode("2");
    root.add(node2);
    DefaultMutableTreeNode node1 = new DefaultMutableTreeNode("1");
    root.add(node1);
    DefaultMutableTreeNode node1_2 = new DefaultMutableTreeNode("1_2");
    node1.add(node1_2);
    DefaultMutableTreeNode node1_1 = new DefaultMutableTreeNode("1_1");
    node1.add(node1_1);
    DefaultTreeModel model = new DefaultTreeModel(root);
    TreeUtil.sort(model, Comparator.comparing(Object::toString));
    assertEquals(node1, root.getChildAt(0));
    assertEquals(node2, root.getChildAt(1));
    assertEquals(node1_1, node1.getChildAt(0));
    assertEquals(node1_2, node1.getChildAt(1));
    TreeUtil.sort(model, (o1, o2) -> {
      TreeNode n1 = (TreeNode) o1;
      TreeNode n2 = (TreeNode) o2;
      return n1.getChildCount() - n2.getChildCount();
    });
    assertEquals(node2, root.getChildAt(0));
    assertEquals(node1, root.getChildAt(1));
  }

  public void testTraverseDepth() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("0");
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("00");
    root.add(node);
    node.add(new DefaultMutableTreeNode("000"));
    node.add(new DefaultMutableTreeNode("001"));
    root.add(new DefaultMutableTreeNode("01"));
    final ArrayList order = new ArrayList();
    TreeUtil.traverseDepth(root, node1 -> {
      order.add(node1.toString());
      return true;
    });
    assertThat(order).containsExactly("0", "00", "000", "001","01");
  }

  public static void waitForTestOnEDT(@NotNull Runnable test) {
    if (EventQueue.isDispatchThread()) {
      test.run();
    }
    else {
      try {
        EventQueue.invokeAndWait(test);
      }
      catch (InterruptedException exception) {
        throw new AssertionError(exception);
      }
      catch (InvocationTargetException exception) {
        Throwable target = exception.getTargetException();
        ExceptionUtil.rethrowUnchecked(target);
        throw new AssertionError(target != null ? target : exception);
      }
    }
  }
}
