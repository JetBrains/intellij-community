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

import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.frame.WatchInplaceEditor;
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
  private List<WatchNode> myChildren;
  private List<XDebuggerTreeNode> myLoadedChildren;
  private XDebuggerEvaluator myCurrentEvaluator;

  public WatchesRootNode(final XDebuggerTree tree, String[] watchExpressions) {
    super(tree, null, false);
    myChildren = new ArrayList<WatchNode>();
    for (String watchExpression : watchExpressions) {
      myChildren.add(WatchMessageNode.createMessageNode(tree, this, watchExpression));
    }
  }

  public void updateWatches(@Nullable XDebuggerEvaluator evaluator) {
    myCurrentEvaluator = evaluator;
    List<WatchNode> newChildren = new ArrayList<WatchNode>();
    if (evaluator != null) {
      for (WatchNode child : myChildren) {
        final String expression = child.getExpression();
        final WatchMessageNode evaluatingNode = WatchMessageNode.createEvaluatingNode(myTree, this, expression);
        newChildren.add(evaluatingNode);
        evaluator.evaluate(expression, new MyEvaluationCallback(evaluatingNode), null);
      }
    }
    else {
      for (WatchNode child : myChildren) {
        final String expression = child.getExpression();
        newChildren.add(WatchMessageNode.createMessageNode(myTree, this, expression));
      }
    }
    myChildren = newChildren;
    myLoadedChildren = null;
    fireNodeChildrenChanged();
  }

  protected List<? extends TreeNode> getChildren() {
    return myChildren;
  }

  @Nullable
  public List<? extends WatchNode> getAllChildren() {
    return myChildren;
  }

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
    for (int i = 0; i < myChildren.size(); i++) {
      WatchNode child = myChildren.get(i);
      if (child == oldNode) {
        myChildren.set(i, newNode);
        if (newNode instanceof XValueContainerNode<?>) {
          myLoadedChildren = null;
          fireNodeChildrenChanged();
          myTree.childrenLoaded(this, Collections.<XValueContainerNode<?>>singletonList((XValueContainerNode<?>)newNode), false);
        }
        else {
          fireNodeChildrenChanged();
        }
        return;
      }
    }
  }

  public void addWatchExpression(final @NotNull XDebuggerEvaluator evaluator, final @NotNull String expression, int index) {
    WatchNode message = WatchMessageNode.createEvaluatingNode(myTree, this, expression);
    if (index == -1) {
      myChildren.add(message);
    }
    else {
      myChildren.add(index, message);
    }
    evaluator.evaluate(expression, new MyEvaluationCallback(message), null);
    fireNodeChildrenChanged();
  }

  public int removeChildNode(XDebuggerTreeNode node) {
    int index = myChildren.indexOf(node);
    myChildren.remove(node);
    myLoadedChildren = null;
    fireNodeChildrenChanged();
    return index;
  }

  public void removeChildren(Collection<? extends XDebuggerTreeNode> nodes) {
    myChildren.removeAll(nodes);
    myLoadedChildren = null;
    fireNodeChildrenChanged();
  }

  public void addNewWatch() {
    editWatch(null);
  }

  public void editWatch(@Nullable WatchNode node) {
    WatchNode messageNode = WatchMessageNode.createMessageNode(myTree, this, "");
    int index = node != null ? myChildren.indexOf(node) : -1;
    if (index == -1) {
      myChildren.add(messageNode);
    }
    else {
      myChildren.set(index, messageNode);
    }
    fireNodeChildrenChanged();
    WatchInplaceEditor editor = new WatchInplaceEditor(this, messageNode, "watch", node);
    editor.show();
  }

  private class MyEvaluationCallback extends XEvaluationCallbackBase {
    private final WatchNode myResultPlace;

    public MyEvaluationCallback(final @NotNull WatchNode resultPlace) {
      myResultPlace = resultPlace;
    }

    public void evaluated(@NotNull final XValue result) {
      DebuggerUIUtil.invokeLater(new Runnable() {
        public void run() {
          replaceNode(myResultPlace, new WatchNodeImpl(myTree, WatchesRootNode.this, result, myResultPlace.getExpression()));
        }
      });
    }

    public void errorOccurred(@NotNull final String errorMessage) {
      DebuggerUIUtil.invokeLater(new Runnable() {
        public void run() {
          replaceNode(myResultPlace, WatchMessageNode.createErrorNode(myTree, WatchesRootNode.this, myResultPlace.getExpression(), errorMessage));
        }
      });
    }
  }
}
