/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.frame.WatchInplaceEditor;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class WatchesRootNode extends XValueContainerNode<XValueContainer> {
  private final XWatchesView myWatchesView;
  private final List<WatchNodeImpl> myChildren;

  @SuppressWarnings("unused")
  // required for com.google.gct.core
  public WatchesRootNode(@NotNull XDebuggerTree tree,
                         @NotNull XWatchesView watchesView,
                         @NotNull XExpression[] expressions) {
    this(tree, watchesView, Arrays.asList(expressions), null, false);
  }

  public WatchesRootNode(@NotNull XDebuggerTree tree,
                         @NotNull XWatchesView watchesView,
                         @NotNull List<XExpression> expressions,
                         @Nullable XStackFrame stackFrame,
                         boolean watchesInVariables) {
    super(tree, null, new XValueContainer() {
      @Override
      public void computeChildren(@NotNull XCompositeNode node) {
        if (stackFrame != null && watchesInVariables) {
          stackFrame.computeChildren(node);
        }
        else {
          node.addChildren(XValueChildrenList.EMPTY, true);
        }
      }
    });
    setLeaf(false);
    myWatchesView = watchesView;
    myChildren = ContainerUtil.newArrayList();
    for (XExpression watchExpression : expressions) {
      myChildren.add(new WatchNodeImpl(myTree, this, watchExpression, stackFrame));
    }
  }

  @NotNull
  @Override
  public List<? extends XValueContainerNode<?>> getLoadedChildren() {
    return ContainerUtil.concat(myChildren, super.getLoadedChildren());
  }

  @NotNull
  @Override
  public List<? extends TreeNode> getChildren() {
    List<? extends TreeNode> children = super.getChildren();
    return ContainerUtil.concat(myChildren, children);
  }

  /**
   * @deprecated use {@link #getWatchChildren()} instead
   */
  @NotNull
  public List<? extends WatchNode> getAllChildren() {
    return getWatchChildren();
  }

  @NotNull
  public List<? extends WatchNode> getWatchChildren() {
    return myChildren;
  }

  @Override
  public void clearChildren() {
    super.clearChildren();
    myChildren.clear();
  }

  public void computeWatches() {
    myChildren.forEach(WatchNodeImpl::computePresentationIfNeeded);
  }

  /**
   * @deprecated Use {@link #addWatchExpression(XStackFrame, XExpression, int, boolean)}
   */
  @Deprecated
  public void addWatchExpression(@Nullable XDebuggerEvaluator evaluator,
                                 @NotNull XExpression expression,
                                 int index,
                                 boolean navigateToWatchNode) {
    addWatchExpression((XStackFrame)null, expression, index, navigateToWatchNode);
  }

  public void addWatchExpression(@Nullable XStackFrame stackFrame,
                                 @NotNull XExpression expression,
                                 int index,
                                 boolean navigateToWatchNode) {
    WatchNodeImpl message = new WatchNodeImpl(myTree, this, expression, stackFrame);
    if (index == -1) {
      myChildren.add(message);
      index = myChildren.size() - 1;
    }
    else {
      myChildren.add(index, message);
    }
    fireNodeInserted(index);
    TreeUtil.selectNode(myTree, message);
    if (navigateToWatchNode) {
      myTree.scrollPathToVisible(message.getPath());
    }
  }

  private void fireNodeInserted(int index) {
    myTree.getTreeModel().nodesWereInserted(this, new int[]{index});
  }

  public int removeChildNode(XDebuggerTreeNode node) {
    return removeChildNode(myChildren, node);
  }

  public void removeChildren(Collection<? extends XDebuggerTreeNode> nodes) {
    int[] indices = getNodesIndices(nodes);
    TreeNode[] removed = getChildNodes(indices);
    myChildren.removeAll(nodes);
    fireNodesRemoved(indices, removed);
  }

  public void removeAllChildren() {
    myChildren.clear();
    fireNodeStructureChanged();
  }

  public void moveUp(WatchNode node) {
    int index = getIndex(node);
    if (index > 0) {
      ContainerUtil.swapElements(myChildren, index, index - 1);
    }
    fireNodeStructureChanged();
    getTree().setSelectionRow(index - 1);
  }

  public void moveDown(WatchNode node) {
    int index = getIndex(node);
    if (index < myChildren.size() - 1) {
      ContainerUtil.swapElements(myChildren, index, index + 1);
    }
    fireNodeStructureChanged();
    getTree().setSelectionRow(index + 1);
  }

  public void addNewWatch() {
    editWatch(null);
  }

  public void editWatch(@Nullable WatchNodeImpl node) {
    WatchNodeImpl messageNode;
    int index = node != null ? myChildren.indexOf(node) : -1;
    if (index == -1) {
      int selectedIndex = myChildren.indexOf(ArrayUtil.getFirstElement(myTree.getSelectedNodes(WatchNodeImpl.class, null)));
      int targetIndex = selectedIndex == - 1 ? myChildren.size() : selectedIndex + 1;
      messageNode = new WatchNodeImpl(myTree, this, XExpressionImpl.EMPTY_EXPRESSION, null);
      myChildren.add(targetIndex, messageNode);
      fireNodeInserted(targetIndex);
      getTree().setSelectionRows(ArrayUtil.EMPTY_INT_ARRAY);
    }
    else {
      messageNode = node;
    }
    new WatchInplaceEditor(this, myWatchesView, messageNode, node).show();
  }
}
