// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.xdebugger.XNamedTreeNode;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class XDebuggerTreeRestorer implements XDebuggerTreeListener, TreeSelectionListener {
  public static final String SELECTION_PATH_PROPERTY = "selection.path";
  private final XDebuggerTree myTree;
  private final Rectangle myLastVisibleNodeRect;
  private final Map<XDebuggerTreeNode, XDebuggerTreeState.NodeInfo> myNode2State = new HashMap<>();
  private final Map<RestorableStateNode, XDebuggerTreeState.NodeInfo> myPendingNode2State = new HashMap<>();
  private final Map<RestorableStateNode, XDebuggerTreeState.NodeInfo> myNode2ParentState = new HashMap<>();
  private boolean myStopRestoringSelection;
  private boolean myInsideRestoring;
  private final TreePath mySelectionPath;
  private final @NotNull CompletableFuture<XDebuggerTree> myFinished = new CompletableFuture<>();

  public XDebuggerTreeRestorer(final XDebuggerTree tree, Rectangle lastVisibleNodeRect) {
    myTree = tree;
    myLastVisibleNodeRect = lastVisibleNodeRect;
    mySelectionPath = (TreePath)myTree.getClientProperty(SELECTION_PATH_PROPERTY);
    myTree.putClientProperty(SELECTION_PATH_PROPERTY, null);
    tree.addTreeListener(this);
    tree.addTreeSelectionListener(this);
  }

  private void restoreChildren(final XDebuggerTreeNode treeNode, final XDebuggerTreeState.NodeInfo nodeInfo) {
    if (nodeInfo.isExpanded()) {
      myTree.expandPath(treeNode.getPath());
      treeNode.getLoadedChildren().forEach(child -> restoreNode(child, nodeInfo));
      myNode2State.put(treeNode, nodeInfo);
    }
  }

  void restore(final XDebuggerTreeNode treeNode, final XDebuggerTreeState.NodeInfo parentInfo) {
    if (treeNode instanceof RestorableStateNode) {
      doRestoreNode((RestorableStateNode)treeNode, parentInfo);
    }
    else {
      restoreChildren(treeNode, parentInfo);
    }
  }

  private void restoreNode(final XDebuggerTreeNode treeNode, final XDebuggerTreeState.NodeInfo parentInfo) {
    if (treeNode instanceof RestorableStateNode node) {
      if (node.isComputed()) {
        doRestoreNode(node, parentInfo.getChild(node));
      }
      else {
        myNode2ParentState.put(node, parentInfo);
      }
    }
  }

  private void doRestoreNode(final RestorableStateNode treeNode, final XDebuggerTreeState.NodeInfo nodeInfo) {
    if (nodeInfo != null) {
      if (!checkExtendedModified(treeNode) && !(Objects.equals(nodeInfo.getValue(), treeNode.getRawValue()))) {
        treeNode.markChanged();
      }
      if (!myStopRestoringSelection && nodeInfo.isSelected() && mySelectionPath == null) {
        try {
          myInsideRestoring = true;
          myTree.addSelectionPath(treeNode.getPath());
        }
        finally {
          myInsideRestoring = false;
        }
      }

      if (!(treeNode.isComputed() && treeNode.isLeaf())) { // do not restore computed leafs children
        restoreChildren((XDebuggerTreeNode)treeNode, nodeInfo);
      }
      else {
        myPendingNode2State.put(treeNode, nodeInfo);
      }
    }
    else {
      if (!checkExtendedModified(treeNode)) {
        treeNode.markChanged();
      }
      if (mySelectionPath != null && !myStopRestoringSelection && pathsEqual(mySelectionPath, treeNode.getPath())) {
        myTree.addSelectionPath(treeNode.getPath());
      }
    }
  }

  // comparing only named nodes
  private static boolean pathsEqual(@NotNull TreePath path1, @NotNull TreePath path2) {
    if (path1.getPathCount() != path2.getPathCount()) {
      return false;
    }
    do {
      Object component1 = path1.getLastPathComponent();
      Object component2 = path2.getLastPathComponent();
      if (component1 instanceof XNamedTreeNode && component2 instanceof XNamedTreeNode) {
        if (!Objects.equals(((XNamedTreeNode)component1).getName(), ((XNamedTreeNode)component2).getName())) {
          return false;
        }
      }
      path1 = path1.getParentPath();
      path2 = path2.getParentPath();
    } while (path1 != null && path2 != null);
    return true;
  }

  private static boolean checkExtendedModified(RestorableStateNode treeNode) {
    if (treeNode instanceof XValueNodeImpl) {
      XValuePresentation presentation = ((XValueNodeImpl)treeNode).getValuePresentation();
      if (presentation instanceof XValueExtendedPresentation) {
        if (((XValueExtendedPresentation)presentation).isModified()) {
          treeNode.markChanged();
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public void nodeLoaded(@NotNull final RestorableStateNode node, @NotNull final String name) {
    XDebuggerTreeState.NodeInfo parentInfo = myNode2ParentState.remove(node);
    XDebuggerTreeState.NodeInfo nodeInfo = parentInfo != null ? parentInfo.getChild(node) : myPendingNode2State.remove(node);
    // always restore if parentInfo is available
    if (parentInfo != null || nodeInfo != null) {
      doRestoreNode(node, nodeInfo);
    }
    checkFinished();
  }

  private void checkFinished() {
    if (myNode2ParentState.isEmpty() && myNode2State.isEmpty() && myFinished.complete(myTree)) {
      if (myLastVisibleNodeRect != null) {
        myTree.scrollRectToVisible(myLastVisibleNodeRect);
      }
      //dispose(); // do not dispose here, we still need tree listeners for late renderers
    }
  }

  @Override
  public void childrenLoaded(@NotNull final XDebuggerTreeNode node, @NotNull final List<? extends XValueContainerNode<?>> children, final boolean last) {
    XDebuggerTreeState.NodeInfo nodeInfo = myNode2State.get(node);
    if (nodeInfo != null) {
      for (XDebuggerTreeNode child : children) {
        restoreNode(child, nodeInfo);
      }
    }
    if (last) {
      myNode2State.remove(node);
      checkFinished();
    }
  }

  public void dispose() {
    myFinished.cancel(false);
    myNode2ParentState.clear();
    myNode2State.clear();
    myTree.removeTreeListener(this);
    myTree.removeTreeSelectionListener(this);
  }

  public @NotNull CompletableFuture<XDebuggerTree> onFinished() {
    return myFinished.copy();
  }

  public boolean isFinished() {
    return myFinished.isDone() && !myFinished.isCompletedExceptionally();
  }

  @Override
  public void valueChanged(TreeSelectionEvent e) {
    if (!myInsideRestoring) {
      myStopRestoringSelection = true;
    }
  }
}
