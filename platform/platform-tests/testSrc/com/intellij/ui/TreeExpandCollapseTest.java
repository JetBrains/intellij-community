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
package com.intellij.ui;

import com.intellij.util.ui.tree.TreeUtil;
import junit.framework.TestCase;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public class TreeExpandCollapseTest extends TestCase {
  private final DefaultMutableTreeNode myRoot = new DefaultMutableTreeNode();
  private final DefaultTreeModel myTreeModel = new DefaultTreeModel(myRoot);
  private JTree myTree = new JTree(myTreeModel);
  private final DefaultMutableTreeNode myChildA = new DefaultMutableTreeNode();
  private final DefaultMutableTreeNode myChild2 = new DefaultMutableTreeNode();
  private TreePath myChildAPath;
  private TreePath myChild2Path;
  private final DefaultMutableTreeNode myChildB = new DefaultMutableTreeNode();
  private TreePath myChildBPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTreeModel.insertNodeInto(myChildA, myRoot, 0);
    myTreeModel.insertNodeInto(myChildB, myRoot, 1);
    myTreeModel.insertNodeInto(myChild2, myChildA, 0);
    myTreeModel.insertNodeInto(new DefaultMutableTreeNode(), myChild2, 0);
    myTreeModel.insertNodeInto(new DefaultMutableTreeNode(), myChildB, 0);
    myChildAPath = TreeUtil.getPathFromRoot(myChildA);
    myChild2Path = TreeUtil.getPathFromRoot(myChild2);
    myChildBPath = TreeUtil.getPathFromRoot(myChildB);
  }

  public void testCollapse() {
    myTree.expandPath(myChildAPath);
    myTree.expandPath(myChild2Path);
    checkExpanded(myChild2Path);
    myTree.setSelectionPath(myChild2Path);
    TreeExpandCollapse.collapse(myTree);
    checkCollapsed(myChild2Path);
    checkExpanded(myChildAPath);
  }

  public void testCollapseWithoutSelection() {
    myTree.clearSelection();
    TreeExpandCollapse.collapse(myTree);
  }

  public void testExpandWithoutSelection() {
    myTree.clearSelection();
    TreeExpandCollapse.expand(myTree);
  }

  public void testExpand() {
    TreePath rootPath = collapseRoot();
    myTree.setSelectionPath(rootPath);
    TreeExpandCollapse.expand(myTree);
    checkExpanded(rootPath);
    checkCollapsed(myChildAPath);
  }

  public void testTotalFiniteExpand() {
    TreePath rootPath = collapseRoot();
    myTree.setSelectionPath(rootPath);
    TreeExpandCollapse.expandAll(myTree);
    checkExpanded(rootPath);
    checkExpanded(myChildAPath);
    checkExpanded(myChild2Path);
  }

  public void testInfiniteExpand() {
    InfiniteTreeModel model = new InfiniteTreeModel();
    myTree = new JTree(model);
    TreePath rootPath = new TreePath(model.getRoot());
    myTree.setSelectionPath(rootPath);
    myTree.collapsePath(rootPath);
    TreeExpandCollapse.expandAll(myTree);
    checkExpanded(rootPath);
    TreeExpandCollapse.expandAll(myTree);
  }

  public void testSubsequentExpand() {
    InfiniteTreeModel model = new InfiniteTreeModel();
    myTree = new JTree(model);
    TreeExpandCollapse.expandAll(myTree);
    TreePath path = new TreePath(model.getRoot());
    while (myTree.isExpanded(path)) path = path.pathByAddingChild(model.getChild(path.getLastPathComponent(), 0));
    checkCollapsed(path);
    TreeExpandCollapse.expandAll(myTree);
    checkExpanded(path);
  }

  public void testTotalExpandWithoutSelection() {
    collapseRoot();
    myTree.clearSelection();
    TreeExpandCollapse.expand(myTree);
    checkCollapsed(new TreePath(myRoot));
    checkCollapsed(myChildAPath);
    checkCollapsed(myChildBPath);
  }

  public void testTotalExpandWithSelection() {
    myTree.expandPath(new TreePath(myRoot));
    myTree.collapsePath(myChildAPath);
    myTree.collapsePath(myChildBPath);
    myTree.setSelectionPath(myChildAPath);
    TreeExpandCollapse.expandAll(myTree);
    checkExpanded(myChildAPath);
    checkCollapsed(myChildBPath);
  }

  public void testExpandAllWithManyLeafs() {
    collapseRoot();
    addChildren(500, myChild2);
    addChildren(500, myChildA);
    addChildren(500, myChildB);
    myTree.setSelectionPath(new TreePath(myRoot));
    TreeExpandCollapse.expandAll(myTree);
    checkExpanded(new TreePath(myRoot));
    checkExpanded(myChildAPath);
    checkExpanded(myChildBPath);
    checkExpanded(myChild2Path);
  }

  public void testExpandManyNotLeafs() {
    collapseRoot();
    TreePath[] treePaths = new TreePath[20];
    for (int i = 0; i < treePaths.length; i++) {
      DefaultMutableTreeNode child = new DefaultMutableTreeNode();
      myTreeModel.insertNodeInto(child, myChild2, 0);
      addChildren(20, child);
      treePaths[i] = myChild2Path.pathByAddingChild(child);
    }
    TreeExpandCollapse.expandAll(myTree);
    for (int i = 0; i < treePaths.length; i++) {
      TreePath treePath = treePaths[i];
      checkExpanded(treePath);
    }
  }

  private void addChildren(int childCount, DefaultMutableTreeNode node) {
    for (int i = 0; i < childCount; i++) {
      myTreeModel.insertNodeInto(new DefaultMutableTreeNode(), node, 0);
    }
  }

  private TreePath collapseRoot() {
    TreePath rootPath = new TreePath(myRoot);
    myTree.collapsePath(rootPath);
    myTree.setSelectionPath(rootPath);
    checkCollapsed(rootPath);
    return rootPath;
  }

  private void checkCollapsed(TreePath path) {
    assertFalse(myTree.isExpanded(path));
  }

  private void checkExpanded(TreePath path) {
    assertTrue(myTree.isExpanded(path));
  }

  static class InfiniteTreeModel extends BasicTreeModel {
    @Override
    public Object getRoot() {return new Integer(1); }

    @Override
    public Object getChild(Object parent, int index) {
      return new Integer(intValueOf(parent) + index + 1);
    }

    private int intValueOf(Object parent) {
      Integer i = (Integer) parent;
      int intValue = i.intValue();
      return intValue;
    }

    @Override
    public int getChildCount(Object parent) {return Math.min(3, intValueOf(parent)); }

    @Override
    public int getIndexOfChild(Object parent, Object child) {return intValueOf(child) - intValueOf(parent) - 1; }
  }
}
