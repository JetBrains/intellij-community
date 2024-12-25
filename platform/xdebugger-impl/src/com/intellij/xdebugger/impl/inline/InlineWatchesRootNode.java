// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.inline;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.frame.WatchInplaceEditor;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class InlineWatchesRootNode extends WatchesRootNode {
  private final @NotNull XWatchesView myWatchesView;
  private final XValueGroupNodeImpl myInlinesRootNode;
  private final InlinesGroup myInlinesGroup;

  /**
   * @deprecated Use {@link InlineWatchesRootNode#InlineWatchesRootNode(XDebuggerTree, XWatchesView, String, XStackFrame, boolean)} instead
   */
  @Deprecated
  public InlineWatchesRootNode(@NotNull XDebuggerTree tree,
                               @NotNull XWatchesView watchesView,
                               @NotNull List<XExpression> regularWatchesExpressions,
                               @NotNull List<InlineWatch> inlineWatchesExpressions,
                               @Nullable XStackFrame stackFrame,
                               boolean watchesInVariables) {
    this(tree, watchesView,
         Objects.requireNonNull(((XVariablesView)watchesView).getSession()).getSessionData().getConfigurationName(),
         stackFrame, watchesInVariables);
  }

  public InlineWatchesRootNode(@NotNull XDebuggerTree tree,
                               @NotNull XWatchesView watchesView,
                               @NotNull String configurationName,
                               @Nullable XStackFrame stackFrame,
                               boolean watchesInVariables) {
    super(tree, watchesView, configurationName, stackFrame, watchesInVariables);
    myWatchesView = watchesView;
    myInlinesGroup = new InlinesGroup(XDebuggerBundle.message("debugger.inline.watches.group.name"), true);
    myInlinesRootNode = new XValueGroupNodeImpl(tree, this, myInlinesGroup) {
      @Override
      public @NotNull List<? extends TreeNode> getChildren() {
        return myInlinesGroup.getChildren();
      }
    };
    List<InlineWatch> inlineWatches = ((XDebuggerManagerImpl)XDebuggerManager.getInstance(tree.getProject()))
      .getWatchesManager().getInlineWatches();

    for (InlineWatch inlineWatch : inlineWatches) {
      myInlinesGroup.getChildren().add(new InlineWatchNodeImpl(myTree, myInlinesRootNode, inlineWatch, stackFrame));
    }
  }

  public void addInlineWatchExpression(XStackFrame frame,
                                       InlineWatch watch,
                                       int index,
                                       boolean navigateToWatchNode) {
    InlineWatchNodeImpl message = new InlineWatchNodeImpl(myTree, myInlinesRootNode, watch, frame);
    if (index == -1) {
      myInlinesGroup.getChildren().add(message);
      index = myInlinesGroup.getChildren().size() - 1;
    }
    else {
      myInlinesGroup.getChildren().add(index, message);
    }
    if (myInlinesGroup.getChildren().size() == 1) {
      myTree.getTreeModel().reload();
    }
    fireInlineNodeInserted(index);
    message.computePresentationIfNeeded();
    TreeUtil.selectNode(myTree, message);
    if (navigateToWatchNode) {
      myTree.scrollPathToVisible(message.getPath());
    }
  }

  public void fireInlineNodeInserted(int index) {
    myTree.getTreeModel().nodesWereInserted(myInlinesRootNode, new int[]{index});
  }

  public void removeInlineChildren(List<InlineWatchNode> inlines) {
    List<? extends InlineWatchNode> children = getInlineWatchChildren();
    int[] indices = inlines.stream().mapToInt(node -> children.indexOf(node)).filter(ind -> ind >= 0).toArray();
    TreeNode[] removed = Arrays.stream(indices).mapToObj(ind -> ((TreeNode)children.get(ind))).toArray(s -> new TreeNode[s]);
    children.removeAll(inlines);

    if (indices.length > 0) {
      inlineNodesRemoved(indices, removed);
    }
    if (children.isEmpty()) {
      allChildrenRemoved();
    }
  }

  private void inlineNodesRemoved(int[] indices, TreeNode[] removed) {
    myTree.getTreeModel().nodesWereRemoved(myInlinesRootNode, indices, removed);
    for (TreeNode node : removed) {
      ((InlineWatchNodeImpl)node).nodeRemoved();
    }
  }

  private void allChildrenRemoved() {
    myTree.getTreeModel().nodesWereRemoved(this, new int[]{0}, new XValueGroupNodeImpl[]{myInlinesRootNode});
  }

  static class InlinesGroup extends XValueGroup {
    private final boolean myInlinesInWatches;
    private final List<InlineWatchNodeImpl> myChildren;

    protected InlinesGroup(@NotNull String name, boolean inlinesInWatches) {
      super(name);
      this.myInlinesInWatches = inlinesInWatches;
      myChildren = new ArrayList<>();
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      if (myInlinesInWatches) {
        XValueChildrenList list = new XValueChildrenList();
        for (InlineWatchNodeImpl child : myChildren) {
          list.add((XNamedValue)child.getValueContainer());
        }
        node.addChildren(list, true);
      }
      else {
        node.addChildren(XValueChildrenList.EMPTY, true);
      }
    }

    List<InlineWatchNodeImpl> getChildren() {
      return myChildren;
    }
  }


  @Override
  public @NotNull @Unmodifiable List<? extends XValueContainerNode<?>> getLoadedChildren() {
    List<? extends XValueContainerNode<?>> children = super.getLoadedChildren();
    if (inlinesRootNodeIsShown()) {
      return ContainerUtil.prepend(children, myInlinesRootNode);
    }
    else {
      return children;
    }
  }

  @Override
  public @NotNull @Unmodifiable List<? extends TreeNode> getChildren() {
    List<? extends TreeNode> children = super.getChildren();
    if (myInlinesRootNode != null && inlinesRootNodeIsShown()) {
      return ContainerUtil.prepend(children, myInlinesRootNode);
    }
    else {
      return children;
    }
  }

  public boolean inlinesRootNodeIsShown() {
    return !getInlineWatchChildren().isEmpty();
  }

  public @NotNull List<? extends InlineWatchNode> getInlineWatchChildren() {
    return myInlinesGroup.myChildren;
  }

  @Override
  public void clearChildren() {
    super.clearChildren();
    getInlineWatchChildren().clear();
  }

  @Override
  public void computeWatches() {
    super.computeWatches();
    ((List<InlineWatchNodeImpl>)getInlineWatchChildren()).forEach(WatchNodeImpl::computePresentationIfNeeded);
  }

  @Override
  public int removeChildNode(XDebuggerTreeNode node) {
    if (node instanceof InlineWatchNodeImpl) {
      List<? extends InlineWatchNode> children = getInlineWatchChildren();
      int index = children.indexOf(node);
      if (index != -1) {
        children.remove(node);
        inlineNodesRemoved(new int[]{index}, new TreeNode[]{node});
      }
      if (children.isEmpty()) {
        allChildrenRemoved();
      }
      return index;
    }
    else {
      return super.removeChildNode(node);
    }
  }


  @Override
  public void removeAllChildren() {
    getInlineWatchChildren().clear();
    fireNodeStructureChanged(myInlinesRootNode);
    super.removeAllChildren();
  }

  @Override
  public void editWatch(@Nullable WatchNodeImpl node) {
    if (node instanceof InlineWatchNodeImpl) {
      new WatchInplaceEditor(this, myWatchesView, node, node).show();
    }
    else {
      super.editWatch(node);
    }
  }

  @Override
  public int headerNodesCount() {
    return inlinesRootNodeIsShown() ? 1 : 0;
  }

  @Override
  public void moveUp(WatchNode node) {
    int index = getIndex(node);
    if (inlinesRootNodeIsShown()) {
      index--;
    }
    if (index > 0) {
      ContainerUtil.swapElements(getWatchChildren(), index, index - 1);
    }
    fireNodeStructureChanged();
    int selectionRow = inlinesRootNodeIsShown() ? index : index - 1;
    getTree().setSelectionRow(selectionRow);
  }

  @Override
  public void moveDown(WatchNode node) {
    int index = getIndex(node);
    if (inlinesRootNodeIsShown()) {
      index--;
    }
    if (index < getWatchChildren().size() - 1) {
      ContainerUtil.swapElements(getWatchChildren(), index, index + 1);
    }
    fireNodeStructureChanged();
    int selectionRow = inlinesRootNodeIsShown() ? index + 2 : index + 1;
    getTree().setSelectionRow(selectionRow);
  }
}
