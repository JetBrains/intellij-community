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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XErrorValuePresentation;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.frame.WatchInplaceEditor;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.xdebugger.impl.frame.XDebugView.getSession;

/**
 * @author nik
 */
public class WatchesRootNode extends XValueContainerNode<XValueContainer> {
  private final XWatchesView myWatchesView;
  private final List<WatchNodeImpl> myChildren;

  public WatchesRootNode(@NotNull XDebuggerTree tree,
                         @NotNull XWatchesView watchesView,
                         @NotNull XExpression[] expressions) {
    this(tree, watchesView, expressions, null);
  }

  public WatchesRootNode(@NotNull XDebuggerTree tree,
                         @NotNull XWatchesView watchesView,
                         @NotNull XExpression[] expressions,
                         @Nullable XStackFrame stackFrame) {
    super(tree, null, new XValueContainer() {
      @Override
      public void computeChildren(@NotNull XCompositeNode node) {
        if (stackFrame != null && Registry.is("debugger.watches.in.variables")) {
          stackFrame.computeChildren(node);
        }
        else {
          node.addChildren(XValueChildrenList.EMPTY, true);
        }
        XDebuggerEvaluator evaluator = stackFrame == null ? null : stackFrame.getEvaluator();
        WatchesRootNode thisNode = (WatchesRootNode)node;
        for (WatchNodeImpl child : thisNode.myChildren) {
          MyEvaluationCallback callback = new MyEvaluationCallback(child);
          if (evaluator != null) {
            evaluator.evaluate(child.getExpression(), callback, stackFrame.getSourcePosition());
          }
          else {
            callback.noSession();
          }
        }
      }
    });
    setLeaf(false);
    myWatchesView = watchesView;
    myChildren = ContainerUtil.newArrayList();
    for (XExpression watchExpression : expressions) {
      myChildren.add(createEvaluatingNode(myTree, this, watchExpression));
    }
  }

  @Nullable
  @Override
  public List<? extends XValueContainerNode<?>> getLoadedChildren() {
    List<? extends XValueContainerNode<?>> empty = Collections.<XValueGroupNodeImpl>emptyList();
    return ContainerUtil.concat(myChildren, ObjectUtils.notNull(super.getLoadedChildren(), empty));
  }

  @NotNull
  @Override
  public List<? extends TreeNode> getChildren() {
    List<? extends TreeNode> children = super.getChildren();
    return ContainerUtil.concat(myChildren, children);
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

  private void replaceNode(WatchNodeImpl oldNode, WatchNodeImpl newNode) {
    int[] selectedRows = getTree().getSelectionRows();
    for (int i = 0; i < myChildren.size(); i++) {
      WatchNodeImpl child = myChildren.get(i);
      if (child == oldNode) {
        myChildren.set(i, newNode);
        fireNodeStructureChanged(newNode);
        myTree.childrenLoaded(this, Collections.singletonList((XValueContainerNode<?>)newNode), false);
        getTree().setSelectionRows(selectedRows);
        return;
      }
    }
  }

  public void addWatchExpression(final @Nullable XDebuggerEvaluator evaluator,
                                 final @NotNull XExpression expression,
                                 int index, final boolean navigateToWatchNode) {
    WatchNodeImpl message = evaluator != null ? createEvaluatingNode(myTree, this, expression) :
                            createMessageNode(myTree, this, expression);
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
      fireNodesRemoved(new int[]{index}, new TreeNode[]{node});
    }
    return index;
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
      messageNode = createMessageNode(myTree, this, XExpressionImpl.EMPTY_EXPRESSION);
      myChildren.add(targetIndex, messageNode);
      fireNodeInserted(targetIndex);
      getTree().setSelectionRows(ArrayUtil.EMPTY_INT_ARRAY);
    }
    else {
      messageNode = node;
    }
    XDebugSession session = getSession(myTree);
    new WatchInplaceEditor(this, session, myWatchesView, messageNode, "watch", node).show();
  }

  private static class MyEvaluationCallback extends XEvaluationCallbackBase implements Obsolescent {
    private final WatchNodeImpl myResultPlace;

    public MyEvaluationCallback(@NotNull WatchNodeImpl resultPlace) {
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
          WatchesRootNode root = (WatchesRootNode)myResultPlace.getParent();
          root.replaceNode(myResultPlace, new WatchNodeImpl(root.myTree, root, result, myResultPlace.getExpression()));
        }
      });
    }

    @Override
    public void errorOccurred(@NotNull final String errorMessage) {
      DebuggerUIUtil.invokeLater(new Runnable() {
        @Override
        public void run() {
          WatchesRootNode root = (WatchesRootNode)myResultPlace.getParent();
          root.replaceNode(myResultPlace, createErrorNode(root.myTree, root, myResultPlace.getExpression(), errorMessage));
        }
      });
    }

    public void noSession() {
      DebuggerUIUtil.invokeLater(new Runnable() {
        @Override
        public void run() {
          WatchesRootNode root = (WatchesRootNode)myResultPlace.getParent();
          root.replaceNode(myResultPlace, createMessageNode(root.myTree, root, myResultPlace.getExpression()));
        }
      });
    }
  }

  private static WatchNodeImpl createMessageNode(XDebuggerTree tree, WatchesRootNode parent, XExpression expression) {
    return new WatchNodeImpl(tree, parent, new XValue() {
      @Override
      public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        node.setPresentation(AllIcons.Debugger.Watch, new XValuePresentation() {
          @NotNull
          @Override
          public String getSeparator() {
            return "";
          }

          @Override
          public void renderValue(@NotNull XValueTextRenderer renderer) {
          }
        }, false);
      }
    }, expression);
  }

  private static WatchNodeImpl createEvaluatingNode(XDebuggerTree tree, WatchesRootNode parent, XExpression expression) {
    return new WatchNodeImpl(tree, parent, new XValue() {
      @Override
      public void computePresentation(@NotNull XValueNode node1, @NotNull XValuePlace place) {
      }
    }, expression);
  }

  private static WatchNodeImpl createErrorNode(XDebuggerTree tree, WatchesRootNode parent,
                                               @NotNull XExpression expression, @NotNull String errorMessage) {
    return new WatchNodeImpl(tree, parent, new XValue() {
      @Override
      public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        node.setPresentation(XDebuggerUIConstants.ERROR_MESSAGE_ICON, new XErrorValuePresentation(errorMessage), false);
      }
    }, expression);
  }
}
