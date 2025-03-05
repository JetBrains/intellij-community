// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.icons.CompositeIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.XAlwaysEvaluatedWatch;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XWatch;
import com.intellij.xdebugger.impl.XWatchImpl;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.frame.WatchInplaceEditor;
import com.intellij.xdebugger.impl.frame.XDebugView;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.pinned.items.PinToTopParentValue;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class WatchesRootNode extends XValueContainerNode<XValueContainer> {
  private final XWatchesView myWatchesView;
  private final List<WatchNodeImpl> myChildren;

  static class RootContainerNode extends XValueContainer implements PinToTopParentValue {
    private final XStackFrame stackFrame;
    private final boolean watchesInVariables;

    RootContainerNode(@Nullable XStackFrame stackFrame,
                      boolean watchesInVariables) {
      this.stackFrame = stackFrame;
      this.watchesInVariables = watchesInVariables;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      if (stackFrame != null && watchesInVariables) {
        stackFrame.computeChildren(node);
      }
      else {
        node.addChildren(XValueChildrenList.EMPTY, true);
      }
    }

    @Override
    public @Nullable String getTag() {
      if (stackFrame instanceof PinToTopParentValue pinToTopParentValue) {
        return pinToTopParentValue.getTag();
      }
      return null;
    }
  }

  /**
   * @deprecated Use {@link WatchesRootNode#WatchesRootNode(XDebuggerTree, XWatchesView, String, XStackFrame, boolean)} instead
   */
  @Deprecated
  // required for com.google.gct.core
  public WatchesRootNode(@NotNull XDebuggerTree tree,
                         @NotNull XWatchesView watchesView,
                         XExpression @NotNull [] expressions) {
    this(tree, watchesView, Arrays.asList(expressions), null, false);
  }

  /**
   * @deprecated Use {@link WatchesRootNode#WatchesRootNode(XDebuggerTree, XWatchesView, String, XStackFrame, boolean)} instead
   */
  @Deprecated
  public WatchesRootNode(@NotNull XDebuggerTree tree,
                         @NotNull XWatchesView watchesView,
                         @NotNull List<? extends XExpression> expressions,
                         @Nullable XStackFrame stackFrame,
                         boolean watchesInVariables) {
    this(tree, watchesView, ContainerUtil.map(expressions, XAlwaysEvaluatedWatch::new), stackFrame, watchesInVariables, false);
  }

  public WatchesRootNode(@NotNull XDebuggerTree tree,
                         @NotNull XWatchesView watchesView,
                         @NotNull String configurationName,
                         @Nullable XStackFrame stackFrame,
                         boolean watchesInVariables) {
    this(tree, watchesView,
         ((XDebuggerManagerImpl)XDebuggerManager.getInstance(tree.getProject())).getWatchesManager().getWatchEntries(configurationName),
         stackFrame, watchesInVariables, false);
  }

  private WatchesRootNode(@NotNull XDebuggerTree tree,
                          @NotNull XWatchesView watchesView,
                          @NotNull List<? extends XWatch> watches,
                          @Nullable XStackFrame stackFrame,
                          boolean watchesInVariables,
                          @SuppressWarnings("unused") boolean avoidSignatureClash) {
    super(tree, null, false, new RootContainerNode(stackFrame, watchesInVariables));
    myWatchesView = watchesView;
    myChildren = new ArrayList<>();
    copyEvaluationResultFromOldRoot(tree, stackFrame);

    for (XWatch watch : watches) {
      myChildren.add(new WatchNodeImpl(myTree, this, watch, stackFrame, null, null));
    }
  }

  private void copyEvaluationResultFromOldRoot(@NotNull XDebuggerTree tree, @Nullable XStackFrame stackFrame) {
    // copy evaluation result by default
    if (stackFrame == null) return;
    if (!(tree.getRoot() instanceof WatchesRootNode watchesRootNode)) return;
    StreamEx.of(watchesRootNode.myChildren)
      .select(ResultNode.class)
      .findFirst()
      .ifPresent(node -> myChildren.add(new ResultNode(myTree, this, node.getExpression(), node.getValueContainer())));
  }

  @Override
  public @NotNull @Unmodifiable List<? extends XValueContainerNode<?>> getLoadedChildren() {
    return ContainerUtil.concat(myChildren, super.getLoadedChildren());
  }

  @Override
  public @NotNull @Unmodifiable List<? extends TreeNode> getChildren() {
    List<? extends TreeNode> children = super.getChildren();
    return ContainerUtil.concat(myChildren, children);
  }

  public @NotNull List<? extends WatchNode> getWatchChildren() {
    return myChildren;
  }

  /**
   * @deprecated Use {@link WatchesRootNode#getWatches()} instead.
   */
  @Deprecated
  public List<XExpression> getWatchExpressions() {
    return StreamEx.of(getWatchChildren())
      .filter(node -> !(node instanceof ResultNode))
      .map(WatchNode::getExpression)
      .toList();
  }

  public List<XWatch> getWatches() {
    return StreamEx.of(getWatchChildren())
      .select(WatchNodeImpl.class)
      .filter(node -> !(node instanceof ResultNode))
      .map(WatchNodeImpl::getXWatch)
      .toList();
  }

  @Override
  public void clearChildren() {
    super.clearChildren();
    myChildren.clear();
  }

  public void computeWatches() {
    myChildren.forEach(WatchNodeImpl::computePresentationIfNeeded);
  }

  static class ResultNode extends WatchNodeImpl {
    ResultNode(@NotNull XDebuggerTree tree,
               @NotNull WatchesRootNode parent,
               @NotNull XExpression expression,
               @Nullable XStackFrame stackFrame) {
      super(tree, parent, new XAlwaysEvaluatedWatch(expression), stackFrame, XDebuggerBundle.message("debugger.result.node.name"), null);
    }

    ResultNode(@NotNull XDebuggerTree tree,
               @NotNull WatchesRootNode parent,
               @NotNull XExpression expression,
               @NotNull XValue value) {
      super(tree, parent, new XAlwaysEvaluatedWatch(expression), null, XDebuggerBundle.message("debugger.result.node.name"), value);
    }

    @Override
    public void applyPresentation(@Nullable Icon icon, @NotNull XValuePresentation valuePresentation, boolean hasChildren) {
      Icon resultIcon = AllIcons.Debugger.Db_evaluateNode;
      if (icon instanceof CompositeIcon) {
        IconUtil.replaceInnerIcon(icon, AllIcons.Debugger.Db_watch, resultIcon);
      }
      else {
        icon = resultIcon;
      }
      super.applyPresentation(icon, valuePresentation, hasChildren);
    }

    @Override
    protected void evaluated() {
      ApplicationManager.getApplication().invokeLater(() -> {
        XDebugSession session = XDebugView.getSession(getTree());
        if (session != null) {
          session.rebuildViews();
        }
      });
    }

    @Override
    public @NotNull XEvaluationOrigin getEvaluationOrigin() {
      return XEvaluationOrigin.INLINE;
    }
  }

  public void removeResultNode() {
    myChildren.removeIf(node -> node instanceof ResultNode);
  }

  public void addResultNode(@Nullable XStackFrame stackFrame, @NotNull XExpression expression) {
    WatchNodeImpl message = new ResultNode(myTree, this, expression, stackFrame);
    if (ContainerUtil.getFirstItem(myChildren) instanceof ResultNode currentResultNode) {
      removeChildNode(currentResultNode);
    }
    myChildren.add(0, message);
    fireNodeInserted(0);
    TreeUtil.selectNode(myTree, message);
  }

  public void addWatchExpression(@Nullable XStackFrame stackFrame,
                                 @NotNull XExpression expression,
                                 int index,
                                 boolean navigateToWatchNode) {
    WatchNodeImpl message = new WatchNodeImpl(myTree, this, new XWatchImpl(expression), stackFrame, null, null);
    if (index < 0 || index > myChildren.size()) {
      index = myChildren.size();
    }
    myChildren.add(index, message);
    fireNodeInserted(index);
    TreeUtil.selectNode(myTree, message);
    if (navigateToWatchNode) {
      myTree.scrollPathToVisible(message.getPath());
    }
  }

  private void fireNodeInserted(int index) {
    myTree.getTreeModel().nodesWereInserted(this, new int[]{index + headerNodesCount()});
  }

  public int removeChildNode(XDebuggerTreeNode node) {
    int index = myChildren.indexOf(node);
    if (index != -1) {
      myChildren.remove(node);
      fireNodesRemoved(new int[]{index + headerNodesCount()}, new TreeNode[]{node});
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
      int targetIndex = selectedIndex == -1 ? myChildren.size() : selectedIndex + 1;
      messageNode = new WatchNodeImpl(myTree, this, XExpressionImpl.EMPTY_EXPRESSION, (XStackFrame)null);
      myChildren.add(targetIndex, messageNode);
      fireNodeInserted(targetIndex);
      getTree().setSelectionRows(ArrayUtilRt.EMPTY_INT_ARRAY);
    }
    else {
      messageNode = node;
    }
    new WatchInplaceEditor(this, myWatchesView, messageNode, node).show();
  }

  public int headerNodesCount() {
    return 0;
  }
}
