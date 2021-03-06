// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.frame.WatchInplaceEditor;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InlineWatchesRootNode extends WatchesRootNode {
  private final @NotNull XWatchesView myWatchesView;
  private final XValueGroupNodeImpl myInlinesRootNode;
  private final InlinesGroup myInlinesGroup;


  public InlineWatchesRootNode(@NotNull XDebuggerTree tree,
                        @NotNull XWatchesView watchesView,
                        @NotNull List<XExpression> regularWatchesExpressions,
                        @NotNull List<InlineWatch> inlineWatchesExpressions,
                        @Nullable XStackFrame stackFrame,
                        boolean watchesInVariables) {
    super(tree, watchesView, regularWatchesExpressions, stackFrame, watchesInVariables);
    myWatchesView = watchesView;
    myInlinesGroup = new InlinesGroup(XDebuggerBundle.message("debugger.inline.watches.group.name"), true);
    myInlinesRootNode = new XValueGroupNodeImpl(tree, this, myInlinesGroup) {
      @Override
      public @NotNull List<? extends TreeNode> getChildren() {
        return myInlinesGroup.getChildren();
      }
    };
    for (InlineWatch watchExpression : inlineWatchesExpressions) {
      myInlinesGroup.getChildren().add(new InlineWatchNodeImpl(myTree, myInlinesRootNode, watchExpression, stackFrame));
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
      inlineNodeRemoved(indices, removed);
    }
    if (children.size() == 0) {
      myTree.getTreeModel().nodesWereRemoved(this, new int[]{0}, new XValueGroupNodeImpl[]{myInlinesRootNode});
    }
  }

  private void inlineNodeRemoved(int[] indices, TreeNode[] removed) {
    myTree.getTreeModel().nodesWereRemoved(myInlinesRootNode, indices, removed);
    for (TreeNode node : removed) {
      ((InlineWatchNodeImpl)node).nodeRemoved();
    }
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


  @NotNull
  @Override
  public List<? extends XValueContainerNode<?>> getLoadedChildren() {
    List<? extends XValueContainerNode<?>> children = super.getLoadedChildren();
    if(inlinesRootNodeIsShown()) {
      return ContainerUtil.prepend(children, myInlinesRootNode);
    } else {
      return children;
    }
  }

  @NotNull
  @Override
  public List<? extends TreeNode> getChildren() {
    List<? extends TreeNode> children = super.getChildren();
    if(myInlinesRootNode != null && inlinesRootNodeIsShown()) {
      return ContainerUtil.prepend(children, myInlinesRootNode);
    } else {
      return children;
    }
  }

  public boolean inlinesRootNodeIsShown() {
    return !getInlineWatchChildren().isEmpty();
  }

  @NotNull
  public List<? extends InlineWatchNode> getInlineWatchChildren() {
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
        inlineNodeRemoved(new int[]{index}, new TreeNode[]{node});
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
    } else {
      super.editWatch(node);
    }
  }

  @Override
  public int headerNodesCount() {
    return inlinesRootNodeIsShown() ? 1 : 0;
  }

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
