/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.util.Comparing;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class XDebuggerTreeRestorer implements XDebuggerTreeListener, TreeSelectionListener {
  private final XDebuggerTree myTree;
  private final Rectangle myLastVisibleNodeRect;
  private final Map<XDebuggerTreeNode, XDebuggerTreeState.NodeInfo> myNode2State = new HashMap<XDebuggerTreeNode, XDebuggerTreeState.NodeInfo>();
  private final Map<RestorableStateNode, XDebuggerTreeState.NodeInfo> myNode2ParentState = new HashMap<RestorableStateNode, XDebuggerTreeState.NodeInfo>();
  private boolean myStopRestoringSelection;
  private boolean myInsideRestoring;

  public XDebuggerTreeRestorer(final XDebuggerTree tree, Rectangle lastVisibleNodeRect) {
    myTree = tree;
    myLastVisibleNodeRect = lastVisibleNodeRect;
    tree.addTreeListener(this);
    tree.addTreeSelectionListener(this);
  }

  private void restoreChildren(final XDebuggerTreeNode treeNode, final XDebuggerTreeState.NodeInfo nodeInfo) {
    if (nodeInfo.isExpanded()) {
      myTree.expandPath(treeNode.getPath());
      List<? extends XDebuggerTreeNode> children = treeNode.getLoadedChildren();
      if (children != null) {
        for (XDebuggerTreeNode child : children) {
          restoreNode(child, nodeInfo);
        }
      }
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
    if (treeNode instanceof RestorableStateNode) {
      RestorableStateNode node = (RestorableStateNode)treeNode;
      if (node.isComputed()) {
        doRestoreNode(node, parentInfo.removeChild(node.getName()));
      }
      else {
        myNode2ParentState.put(node, parentInfo);
      }
    }
  }

  private void doRestoreNode(final RestorableStateNode treeNode, final XDebuggerTreeState.NodeInfo nodeInfo) {
    if (nodeInfo != null) {
      if (!checkExtendedModified(treeNode) && !(Comparing.equal(nodeInfo.getValue(), treeNode.getRawValue()))) {
        treeNode.markChanged();
      }
      if (!myStopRestoringSelection && nodeInfo.isSelected()) {
        try {
          myInsideRestoring = true;
          myTree.addSelectionPath(treeNode.getPath());
        }
        finally {
          myInsideRestoring = false;
        }
      }

      restoreChildren((XDebuggerTreeNode)treeNode, nodeInfo);
    }
    else if (!checkExtendedModified(treeNode)) {
      treeNode.markChanged();
    }
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
  public void nodeLoaded(@NotNull final RestorableStateNode node, final String name) {
    XDebuggerTreeState.NodeInfo parentInfo = myNode2ParentState.remove(node);
    if (parentInfo != null) {
      doRestoreNode(node, parentInfo.removeChild(node.getName()));
    }
    disposeIfFinished();
  }

  private void disposeIfFinished() {
    if (myNode2ParentState.isEmpty() && myNode2State.isEmpty()) {
      if (myLastVisibleNodeRect != null) {
        myTree.scrollRectToVisible(myLastVisibleNodeRect);
      }
      dispose();
    }
  }

  @Override
  public void childrenLoaded(@NotNull final XDebuggerTreeNode node, @NotNull final List<XValueContainerNode<?>> children, final boolean last) {
    XDebuggerTreeState.NodeInfo nodeInfo = myNode2State.get(node);
    if (nodeInfo != null) {
      for (XDebuggerTreeNode child : children) {
        restoreNode(child, nodeInfo);
      }
    }
    if (last) {
      myNode2State.remove(node);
      disposeIfFinished();
    }
  }

  public void dispose() {
    myNode2ParentState.clear();
    myNode2State.clear();
    myTree.removeTreeListener(this);
    myTree.removeTreeSelectionListener(this);
  }

  @Override
  public void valueChanged(TreeSelectionEvent e) {
    if (!myInsideRestoring) {
      myStopRestoringSelection = true;
    }
  }
}
