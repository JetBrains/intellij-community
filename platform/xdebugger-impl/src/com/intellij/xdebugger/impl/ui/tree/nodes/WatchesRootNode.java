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
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.frame.WatchInplaceEditor;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
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
  private List<XDebuggerTreeNode> myChildren;
  private List<XDebuggerTreeNode> myLoadedChildren;
  private final XDebuggerEvaluator myEvaluator;

  public WatchesRootNode(final XDebuggerTree tree, XDebuggerEvaluator evaluator) {
    super(tree, null, false);
    myEvaluator = evaluator;

    loadChildren();
  }

  private void loadChildren() {
    String[] expressions = ((XDebugSessionImpl)myTree.getSession()).getSessionTab().getWatchesView().getWatchExpressions();
    myChildren = new ArrayList<XDebuggerTreeNode>();
    for (String expression : expressions) {
      myChildren.add(MessageTreeNode.createEvaluatingMessage(myTree, this, expression + " = ..."));
    }

    for (int i = 0; i < expressions.length; i++) {
      myEvaluator.evaluate(expressions[i], new MyEvaluationCallback(expressions[i], myChildren.get(i)));
    }
  }

  protected List<? extends TreeNode> getChildren() {
    if (myChildren == null) {
      loadChildren();
    }
    return myChildren;
  }

  @Nullable
  public List<? extends XDebuggerTreeNode> getAllChildren() {
    return myChildren;
  }

  public List<? extends XDebuggerTreeNode> getLoadedChildren() {
    if (myLoadedChildren == null) {
      myLoadedChildren = new ArrayList<XDebuggerTreeNode>();
      for (XDebuggerTreeNode child : myChildren) {
        if (child instanceof WatchNodeImpl) {
          myLoadedChildren.add(child);
        }
      }
    }
    return myLoadedChildren;
  }

  @Override
  public void clearChildren() {
    myChildren = null;
  }

  private void replaceNode(final XDebuggerTreeNode oldNode, final XDebuggerTreeNode newNode) {
    for (int i = 0; i < myChildren.size(); i++) {
      XDebuggerTreeNode child = myChildren.get(i);
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
    MessageTreeNode message = MessageTreeNode.createEvaluatingMessage(myTree, this, expression + "...");
    if (index == -1) {
      myChildren.add(message);
    }
    else {
      myChildren.add(index, message);
    }
    evaluator.evaluate(expression, new MyEvaluationCallback(expression, message));
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
    MessageTreeNode messageNode = MessageTreeNode.createMessageNode(myTree, this, "", null);
    int index = node != null ? myChildren.indexOf(node) : -1;
    if (index == -1) {
      myChildren.add(messageNode);
    }
    else {
      myChildren.set(index, messageNode);
      ((XDebugSessionImpl)myTree.getSession()).getSessionTab().getWatchesView().removeWatchExpression(index);
    }
    fireNodeChildrenChanged();
    WatchInplaceEditor editor = new WatchInplaceEditor(this, messageNode, "watch", node);
    editor.show();
  }

  private class MyEvaluationCallback extends XEvaluationCallbackBase {
    private final String myExpression;
    private final XDebuggerTreeNode myResultPlace;

    public MyEvaluationCallback(final @NotNull String expression, final @NotNull XDebuggerTreeNode resultPlace) {
      myExpression = expression;
      myResultPlace = resultPlace;
    }

    public void evaluated(@NotNull final XValue result) {
      DebuggerUIUtil.invokeLater(new Runnable() {
        public void run() {
          replaceNode(myResultPlace, new WatchNodeImpl(myTree, WatchesRootNode.this, result, myExpression));
        }
      });
    }

    public void errorOccurred(@NotNull final String errorMessage) {
      DebuggerUIUtil.invokeLater(new Runnable() {
        public void run() {
          replaceNode(myResultPlace, new WatchErrorNode(myTree, WatchesRootNode.this, myExpression, errorMessage));
        }
      });
    }
  }
}
