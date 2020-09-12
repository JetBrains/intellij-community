// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerWatchesManager;
import com.intellij.xdebugger.impl.frame.XWatchesViewImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class XInlineWatchesViewImpl extends XWatchesViewImpl implements DnDNativeTarget {
  private final CompositeDisposable myDisposables = new CompositeDisposable();
  private final boolean myWatchesInVariables;

  public XInlineWatchesViewImpl(@NotNull XDebugSessionImpl session, boolean watchesInVariables) {
    this(session, watchesInVariables, watchesInVariables);

  }
  public XInlineWatchesViewImpl(@NotNull XDebugSessionImpl session, boolean watchesInVariables, boolean vertical) {
    super(session, watchesInVariables, vertical);
    myWatchesInVariables = watchesInVariables;
    XDebuggerTree tree = getTree();
    createNewRootNode(null);

    DnDManager.getInstance().registerTarget(this, tree);
  }

  @Override
  protected XValueContainerNode doCreateNewRootNode(@Nullable XStackFrame stackFrame) {
    InlineWatchesRootNode node = new InlineWatchesRootNode(getTree(), this, getExpressions(), getInlineExpressions(), stackFrame, myWatchesInVariables);
    myRootNode = node;
    return node;
  }

  @NotNull
  private List<InlineWatch> getInlineExpressions() {
    return getWatchesManager().getInlineWatches();
  }

  private XDebuggerWatchesManager getWatchesManager() {
    return ((XDebuggerManagerImpl)XDebuggerManager.getInstance(getTree().getProject()))
      .getWatchesManager();
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (XInlineWatchesView.DATA_KEY.is(dataId)) {
      return this;
    }
    return super.getData(dataId);
  }


  public void addInlineWatchExpression(@NotNull InlineWatch watch, int index, boolean navigateToWatchNode) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    XDebugSession session = getSession(getTree());

    ((InlineWatchesRootNode)myRootNode).addInlineWatchExpression(session != null ? session.getCurrentStackFrame() : null, watch, index, navigateToWatchNode);

    if (navigateToWatchNode && session != null) {
      XDebugSessionTab.showWatchesView((XDebugSessionImpl)session);
    }
  }

  @Override
  public void removeWatches(List<? extends XDebuggerTreeNode> nodes) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<? extends XDebuggerTreeNode> ordinaryWatches = ContainerUtil.filter(nodes, node -> !(node instanceof InlineWatchNode));
    super.removeWatches(ordinaryWatches);
    List<? extends XDebuggerTreeNode> inlineWatches = ContainerUtil.filter(nodes, node -> node instanceof InlineWatchNode);
    if (!inlineWatches.isEmpty()) {
      removeNodes(inlineWatches, true);
    }
  }

  private void removeNodes(List<? extends XDebuggerTreeNode> inlineWatches, boolean updateManager) {
    InlineWatchesRootNode rootNode = (InlineWatchesRootNode)myRootNode;
    List<? extends InlineWatchNode> inlineWatchChildren = rootNode.getInlineWatchChildren();
    final int[] minIndex = {Integer.MAX_VALUE};
    List<InlineWatchNode> toRemoveInlines = new ArrayList<>();
    inlineWatches.forEach((node) -> {
      int index = inlineWatchChildren.indexOf(node);
      if (index != -1) {
        toRemoveInlines.add((InlineWatchNode)node);
        minIndex[0] = Math.min(minIndex[0], index);
      }
    });

    rootNode.removeInlineChildren(toRemoveInlines);

    List<? extends InlineWatchNode> newChildren = rootNode.getInlineWatchChildren();
    if (!newChildren.isEmpty()) {
      InlineWatchNode node = newChildren.get(Math.min(minIndex[0], newChildren.size() - 1));
      TreeUtil.selectNode(getTree(), node);
    }
    if (updateManager) {
      getWatchesManager().inlineWatchesRemoved(ContainerUtil.map(toRemoveInlines, node -> node.getWatch()), this);
    }
  }

  public void removeInlineWatches(Collection<InlineWatch> watches) {
    InlineWatchesRootNode rootNode = (InlineWatchesRootNode)myRootNode;
    List<? extends XDebuggerTreeNode> nodesToRemove =
      (List<? extends XDebuggerTreeNode>)ContainerUtil.filter(rootNode.getInlineWatchChildren(), node -> watches.contains(node.getWatch()));

    if (!nodesToRemove.isEmpty()) {
      removeNodes(nodesToRemove, false);
    }
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDisposables);
    DnDManager.getInstance().unregisterTarget(this, getTree());
    super.dispose();
  }

}
