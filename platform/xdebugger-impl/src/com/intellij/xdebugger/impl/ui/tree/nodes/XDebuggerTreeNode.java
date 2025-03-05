// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleColoredText;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public abstract class XDebuggerTreeNode implements TreeNode {
  protected final XDebuggerTree myTree;
  protected final XDebuggerTreeNode myParent;
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
      return Collections.emptyEnumeration();
    }
    return Collections.enumeration(getChildren());
  }

  public abstract @NotNull @Unmodifiable List<? extends TreeNode> getChildren();

  protected void setIcon(final Icon icon) {
    myIcon = icon;
  }

  public void setLeaf(final boolean leaf) {
    myLeaf = leaf;
  }

  public @Nullable XDebuggerTreeNodeHyperlink getLink() {
    return null;
  }

  public @NotNull SimpleColoredText getText() {
    return myText;
  }

  public @Nullable Icon getIcon() {
    return myIcon;
  }

  public @Nullable Object getIconTag() {
    return null;
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

  public abstract @NotNull @Unmodifiable List<? extends XDebuggerTreeNode> getLoadedChildren();

  public abstract void clearChildren();

  public void appendToComponent(@NotNull ColoredTextContainer component) {
    getText().appendToComponent(component);

    XDebuggerTreeNodeHyperlink link = getLink();
    if (link != null) {
      component.append(link.getLinkText(), link.getTextAttributes(), link);
    }
  }

  public void invokeNodeUpdate(Runnable runnable) {
    myTree.invokeLater(runnable);
  }

  @Override
  public String toString() {
    return myText.toString();
  }
}
