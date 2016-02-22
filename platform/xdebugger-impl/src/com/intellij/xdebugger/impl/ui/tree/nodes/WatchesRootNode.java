/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.frame.WatchInplaceEditor;
import com.intellij.xdebugger.impl.frame.XDebugView;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class WatchesRootNode extends XDebuggerTreeNode {
  private final XWatchesView myWatchesView;
  private List<WatchNode> myChildren;
  private List<XDebuggerTreeNode> myLoadedChildren;
  private XDebuggerEvaluator myCurrentEvaluator;

  public WatchesRootNode(@NotNull XDebuggerTree tree,
                         @NotNull XWatchesView watchesView,
                         @NotNull XExpression[] watchExpressions) {
    super(tree, null, false);
    myWatchesView = watchesView;
    myChildren = new ArrayList<WatchNode>();
    for (XExpression watchExpression : watchExpressions) {
      myChildren.add(WatchMessageNode.createMessageNode(tree, this, watchExpression));
    }
  }

  public void updateWatches(@Nullable XDebuggerEvaluator evaluator) {
    myCurrentEvaluator = evaluator;
    List<WatchNode> newChildren = new ArrayList<WatchNode>();
    for (WatchNode child : myChildren) {
      child.setObsolete();
    }
    if (evaluator != null) {
      for (WatchNode child : myChildren) {
        final XExpression expression = child.getExpression();
        final WatchMessageNode evaluatingNode = WatchMessageNode.createEvaluatingNode(myTree, this, expression);
        newChildren.add(evaluatingNode);
        evaluator.evaluate(expression, new MyEvaluationCallback(evaluatingNode), null);
      }
    }
    else {
      for (WatchNode child : myChildren) {
        final XExpression expression = child.getExpression();
        newChildren.add(WatchMessageNode.createMessageNode(myTree, this, expression));
      }
    }
    myChildren = newChildren;
    myLoadedChildren = null;
    fireNodeStructureChanged();
  }

  @NotNull
  @Override
  public List<? extends TreeNode> getChildren() {
    return myChildren;
  }

  @Nullable
  public List<? extends WatchNode> getAllChildren() {
    return myChildren;
  }

  @Override
  public List<? extends XDebuggerTreeNode> getLoadedChildren() {
    if (myLoadedChildren == null) {
      myLoadedChildren = new ArrayList<XDebuggerTreeNode>();
      for (WatchNode child : myChildren) {
        if (child instanceof WatchNodeImpl) {
          myLoadedChildren.add((WatchNodeImpl)child);
        }
      }
    }
    return myLoadedChildren;
  }

  @Override
  public void clearChildren() {
    updateWatches(myCurrentEvaluator);
  }

  private void replaceNode(final WatchNode oldNode, final WatchNode newNode) {
    int[] selectedRows = getTree().getSelectionRows();
    for (int i = 0; i < myChildren.size(); i++) {
      WatchNode child = myChildren.get(i);
      if (child == oldNode) {
        myChildren.set(i, newNode);
        if (newNode instanceof XValueContainerNode<?>) {
          myLoadedChildren = null;
          fireNodeStructureChanged(newNode);
          myTree.childrenLoaded(this, Collections.<XValueContainerNode<?>>singletonList((XValueContainerNode<?>)newNode), false);
        }
        else {
          fireNodeStructureChanged(newNode);
        }
        getTree().setSelectionRows(selectedRows);
        return;
      }
    }
  }

  public void addWatchExpression(final @Nullable XDebuggerEvaluator evaluator,
                                 final @NotNull XExpression expression,
                                 int index, final boolean navigateToWatchNode) {
    WatchMessageNode message = evaluator != null ? WatchMessageNode.createEvaluatingNode(myTree, this, expression) : WatchMessageNode.createMessageNode(myTree, this, expression);
    if (index == -1) {
      myChildren.add(message);
      index = myChildren.size() - 1;
    }
    else {
      myChildren.add(index, message);
    }
    fireNodeInserted(index);
    if (navigateToWatchNode) {
      myTree.scrollPathToVisible(message.getPath());
    }
    if (evaluator != null) {
      evaluator.evaluate(expression, new MyEvaluationCallback(message), null);
    }
  }

  private void fireNodeInserted(int index) {
    myTree.getTreeModel().nodesWereInserted(this, new int[]{index});
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  public int removeChildNode(XDebuggerTreeNode node) {
    int index = myChildren.indexOf(node);
    if (index != -1) {
      myChildren.remove(node);
      myLoadedChildren = null;
      fireNodesRemoved(new int[]{index}, new TreeNode[]{node});
    }
    return index;
  }

  public void removeChildren(Collection<? extends XDebuggerTreeNode> nodes) {
    final int[] indices = getNodesIndices(nodes);
    final TreeNode[] removed = getChildNodes(indices);
    myChildren.removeAll(nodes);
    myLoadedChildren = null;
    fireNodesRemoved(indices, removed);
  }

  public void removeAllChildren() {
    myChildren.clear();
    myLoadedChildren = null;
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

  public void editWatch(@Nullable WatchNode node) {
    WatchNode messageNode = WatchMessageNode.createMessageNode(myTree, this, XExpressionImpl.EMPTY_EXPRESSION);
    int index = node != null ? myChildren.indexOf(node) : -1;
    if (index == -1) {
      myChildren.add(messageNode);
      fireNodeInserted(myChildren.size() - 1);
    }
    else {
      myChildren.set(index, messageNode);
      fireNodeStructureChanged(messageNode);
    }
    XDebugSession session = XDebugView.getSession(myTree);
    new WatchInplaceEditor(this, session, myWatchesView, messageNode, "watch", node).show();
  }

  private class MyEvaluationCallback extends XEvaluationCallbackBase implements Obsolescent {
    private final WatchNode myResultPlace;

    public MyEvaluationCallback(final @NotNull WatchNode resultPlace) {
      myResultPlace = resultPlace;
    }

    @Override
    public boolean isObsolete() {
      return myResultPlace.isObsolete();
    }

    @Override
    public void evaluated(@NotNull final XValue result) {
      DebuggerUIUtil.invokeLater(new Runnable() {
        @Override
        public void run() {
          replaceNode(myResultPlace, new WatchNodeImpl(myTree, WatchesRootNode.this, result, myResultPlace.getExpression()));
        }
      });
    }

    @Override
    public void errorOccurred(@NotNull final String errorMessage) {
      DebuggerUIUtil.invokeLater(new Runnable() {
        @Override
        public void run() {
          replaceNode(myResultPlace, WatchMessageNode.createErrorNode(myTree, WatchesRootNode.this, myResultPlace.getExpression(), errorMessage));
        }
      });
    }
  }
}
