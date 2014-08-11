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
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.enumeration.EmptyEnumeration;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author nik
 */
public abstract class XDebuggerTreeNode implements TreeNode, TreeSpeedSearch.PathAwareTreeNode {
  protected final XDebuggerTree myTree;
  private final XDebuggerTreeNode myParent;
  private boolean myLeaf;
  protected final SimpleColoredText myText = new SimpleColoredText();
  private Icon myIcon;
  private TreePath myPath;

  protected XDebuggerTreeNode(final XDebuggerTree tree, final @Nullable XDebuggerTreeNode parent, final boolean leaf) {
    myParent = parent;
    myLeaf = leaf;
    myTree = tree;
  }

  @Override
  public TreeNode getChildAt(final int childIndex) {
    return isLeaf() ? null : getChildren().get(childIndex);
  }

  @Override
  public int getChildCount() {
    return isLeaf() ? 0 : getChildren().size();
  }

  @Override
  public TreeNode getParent() {
    return myParent;
  }

  @Override
  public int getIndex(@NotNull TreeNode node) {
    if (isLeaf()) return -1;
    return getChildren().indexOf(node);
  }

  @Override
  public boolean getAllowsChildren() {
    return true;
  }

  @Override
  public boolean isLeaf() {
    return myLeaf;
  }

  @Override
  public Enumeration children() {
    if (isLeaf()) {
      return EmptyEnumeration.INSTANCE;
    }
    return Collections.enumeration(getChildren());
  }

  @NotNull
  public abstract List<? extends TreeNode> getChildren();

  protected void setIcon(final Icon icon) {
    myIcon = icon;
  }

  public void setLeaf(final boolean leaf) {
    myLeaf = leaf;
  }

  @Nullable
  protected XDebuggerTreeNodeHyperlink getLink() {
    return null;
  }

  @NotNull
  public SimpleColoredText getText() {
    return myText;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  protected void fireNodeChanged() {
    myTree.getTreeModel().nodeChanged(this);
  }

  protected void fireNodesRemoved(int[] indices, TreeNode[] nodes) {
    if (indices.length > 0) {
      myTree.getTreeModel().nodesWereRemoved(this, indices, nodes);
    }
  }

  protected void fireNodesInserted(Collection<? extends TreeNode> added) {
    if (!added.isEmpty()) {
      myTree.getTreeModel().nodesWereInserted(this, getNodesIndices(added));
    }
  }

  protected TreeNode[] getChildNodes(int[] indices) {
    final TreeNode[] children = new TreeNode[indices.length];
    for (int i = 0; i < indices.length; i++) {
      children[i] = getChildAt(indices[i]);
    }
    return children;
  }

  protected int[] getNodesIndices(@Nullable Collection<? extends TreeNode> children) {
    if (children == null) return ArrayUtilRt.EMPTY_INT_ARRAY;

    final int[] ints = new int[children.size()];
    int i = 0;
    for (TreeNode node : children) {
      ints[i++] = getIndex(node);
    }
    Arrays.sort(ints);
    return ints;
  }

  protected void fireNodeStructureChanged() {
    fireNodeStructureChanged(this);
  }

  protected void fireNodeStructureChanged(final TreeNode node) {
    myTree.getTreeModel().nodeStructureChanged(node);
  }

  public XDebuggerTree getTree() {
    return myTree;
  }

  @Override
  public TreePath getPath() {
    if (myPath == null) {
      TreePath path;
      if (myParent == null) {
        path = new TreePath(this);
      }
      else {
        path = myParent.getPath().pathByAddingChild(this);
      }
      myPath = path;
    }
    return myPath;
  }

  @Nullable
  public abstract List<? extends XDebuggerTreeNode> getLoadedChildren();

  public abstract void clearChildren();

  public void appendToComponent(@NotNull ColoredTextContainer component) {
    getText().appendToComponent(component);

    XDebuggerTreeNodeHyperlink link = getLink();
    if (link != null) {
      component.append(link.getLinkText(), link.getTextAttributes(), link);
    }
  }

  void invokeNodeUpdate(Runnable runnable) {
    myTree.getLaterInvocator().offer(runnable);
  }
}
