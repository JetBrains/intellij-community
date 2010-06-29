/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.tree;

import com.intellij.ui.TreeExpandCollapse;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Assertion;
import junit.framework.TestCase;

import javax.swing.*;
import javax.swing.tree.*;
import java.util.ArrayList;
import java.util.Comparator;

public class TreeUtilTest extends TestCase {
  private final Assertion CHECK = new Assertion();

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
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    DefaultMutableTreeNode child1 = new DefaultMutableTreeNode("1");
    model.insertNodeInto(child1, root, 0);
    DefaultMutableTreeNode child2 = new DefaultMutableTreeNode("2");
    model.insertNodeInto(child2, root, 1);
    DefaultMutableTreeNode child11 = new DefaultMutableTreeNode("11");
    model.insertNodeInto(child11, child1, 0);
    JTree tree = new Tree(model);
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
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root) {
        public void removeNodeFromParent(MutableTreeNode mutableTreeNode) {
          super.removeNodeFromParent((MutableTreeNode) mutableTreeNode.getParent());
        }
      };
    DefaultMutableTreeNode node1 = new DefaultMutableTreeNode("1");
    model.insertNodeInto(node1, root, 0);
    DefaultMutableTreeNode node2 = new DefaultMutableTreeNode("2");
    model.insertNodeInto(node2, node1, 0);
    JTree tree = new Tree(model);
    TreeExpandCollapse.expandAll(tree);
    tree.setSelectionPath(TreeUtil.getPathFromRoot(node2));
    TreeUtil.removeSelected(tree);
    assertEquals(0, root.getChildCount());
    assertEquals(root, tree.getSelectionPath().getLastPathComponent());
  }

  public void testRemoveLast() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    model.insertNodeInto(new DefaultMutableTreeNode("1"), root, 0);
    DefaultMutableTreeNode middle = new DefaultMutableTreeNode("2");
    model.insertNodeInto(middle, root, 1);
    DefaultMutableTreeNode last = new DefaultMutableTreeNode("3");
    model.insertNodeInto(last, root, 2);
    JTree tree = new Tree(model);
    tree.setSelectionPath(TreeUtil.getPathFromRoot(last));
    TreeUtil.removeSelected(tree);
    assertSame(middle, tree.getSelectionPath().getLastPathComponent());
  }

  public void testFindCommonPath() {
    TreePath rootPath = new TreePath("root");
    TreePath path1 = rootPath.pathByAddingChild("1");
    TreePath path1_1 = path1.pathByAddingChild("1_1");
    TreePath path1_2 = path1.pathByAddingChild("1_2");
    TreePath path2_1 = rootPath.pathByAddingChild("2").pathByAddingChild("2_1");
    assertEquals(path1, TreeUtil.findCommonPath(new TreePath[]{path1_1, path1_2}));
    assertEquals(path1, TreeUtil.findCommonPath(new TreePath[]{path1, path1_1}));
    assertEquals(rootPath, TreeUtil.findCommonPath(new TreePath[]{path1_1, path1_2, path2_1}));
  }

  public void testSelectMaximals() {
    String e1 = "a";
    String e2 = "b";
    TreePath path1 = new TreePath(new Object[]{e1, e2, "c"});
    TreePath path2 = new TreePath(new Object[]{e1, e2});
    TreePath path2a = new TreePath(new Object[]{e1, e2});
    TreePath path3 = new TreePath("d");
    TreePath[] maximals = TreeUtil.selectMaximals(new TreePath[]{path1, path2, path3});
    CHECK.compareUnordered(maximals, new TreePath[]{path2, path3});
    assertEquals(1, TreeUtil.selectMaximals(new TreePath[]{path2, path2a}).length);
  }

  public void testSelectMaximalsWhenNone() {
    CHECK.empty(TreeUtil.selectMaximals(null));
    CHECK.empty(TreeUtil.selectMaximals(new TreePath[0]));
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
    TreeUtil.sort(model, new Comparator() {
      public int compare(Object o1, Object o2) {
        return o1.toString().compareTo(o2.toString());
      }
    });
    assertEquals(node1, root.getChildAt(0));
    assertEquals(node2, root.getChildAt(1));
    assertEquals(node1_1, node1.getChildAt(0));
    assertEquals(node1_2, node1.getChildAt(1));
    TreeUtil.sort(model, new Comparator() {
      public int compare(Object o1, Object o2) {
        TreeNode n1 = (TreeNode) o1;
        TreeNode n2 = (TreeNode) o2;
        return n1.getChildCount() - n2.getChildCount();
      }
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
    TreeUtil.traverseDepth(root, new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        order.add(node.toString());
        return true;
      }
    });
    CHECK.compareAll(new String[]{"0", "00", "000", "001","01"}, order);
  }


}
