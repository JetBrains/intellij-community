// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.SmartList;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.frame.XWatchesViewImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XInlineWatchesViewImpl extends XWatchesViewImpl implements XInlineWatchesView ,DnDNativeTarget {
  private final CompositeDisposable myDisposables = new CompositeDisposable();
  private final boolean myWatchesInVariables;
  private boolean myInlineWatchesInWatches;

  public XInlineWatchesViewImpl(@NotNull XDebugSessionImpl session, boolean watchesInVariables,  boolean inlineWatchesInWatches) {
    this(session, watchesInVariables, watchesInVariables, inlineWatchesInWatches);

  }
  public XInlineWatchesViewImpl(@NotNull XDebugSessionImpl session, boolean watchesInVariables, boolean inlineWatchesInWatches, boolean vertical) {
    super(session, watchesInVariables, vertical);
    myWatchesInVariables = watchesInVariables;
    myInlineWatchesInWatches = inlineWatchesInWatches;
    XDebuggerTree tree = getTree();
    createNewRootNode(null);


    DnDManager.getInstance().registerTarget(this, tree);
  }

  @Override
  protected XValueContainerNode doCreateNewRootNode(@Nullable XStackFrame stackFrame) {
    InlineWatchesRootNode node = new InlineWatchesRootNode(getTree(), this, getExpressions(), getInlineExpressions(), stackFrame, myWatchesInVariables, true);
    myRootNode = node;
    return node;
  }

  @Override
  public void updateSessionData() {
    super.updateSessionData();
    List<InlineWatch> expressions = new SmartList<>();
    List<? extends InlineWatchNode> children = ((InlineWatchesRootNode)myRootNode).getInlineWatchChildren();
    for (InlineWatchNode child : children) {
      expressions.add(new InlineWatch(child.getExpression(), child.getPosition()));
    }
    XDebugSession session = getSession(getTree());
    if (session != null) {
      ((XDebugSessionImpl)session).setInlineWatchExpressions(expressions);
    }
    else {
      XDebugSessionData data = getData(XDebugSessionData.DATA_KEY, getTree());
      if (data != null) {
        data.setInlineWatchExpressions(expressions);
      }
    }
  }

  @NotNull
  private List<InlineWatch> getInlineExpressions() {
    XDebuggerTree tree = getTree();
    XDebugSession session = getSession(tree);
    List<InlineWatch> expressions;
    if (session != null) {
      expressions = ((XDebugSessionImpl)session).getSessionData().getInlineWatchExpressions();
    }
    else {
      XDebuggerTreeNode root = tree.getRoot();
      List<? extends InlineWatchNode> current = root instanceof WatchesRootNode
                                          ? ((InlineWatchesRootNode)tree.getRoot()).getInlineWatchChildren() : Collections.emptyList();
      List<InlineWatch> list = new SmartList<>();
      for (InlineWatchNode child : current) {
        list.add(new InlineWatch(child.getExpression(), child.getPosition()));
      }
      expressions = list;
    }
    return expressions;
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (XInlineWatchesView.DATA_KEY.is(dataId)) {
      return this;
    }
    return super.getData(dataId);
  }

  @Override
  public void addInlineWatchExpression(@NotNull XExpression expression, int index, XSourcePosition position, boolean navigateToWatchNode) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    XDebugSession session = getSession(getTree());

    InlineWatch watch = new InlineWatch(expression, position);
    ((InlineWatchesRootNode)myRootNode).addInlineWatchExpression(session != null ? session.getCurrentStackFrame() : null, watch, index, navigateToWatchNode);

    updateSessionData();

    if (navigateToWatchNode && session != null) {
      XDebugSessionTab.showWatchesView((XDebugSessionImpl)session);
    }
  }

  @Override
  public void removeWatches(List<? extends XDebuggerTreeNode> nodes) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    InlineWatchesRootNode rootNode = (InlineWatchesRootNode)myRootNode;
    List<? extends WatchNode> children = rootNode.getWatchChildren();
    List<? extends InlineWatchNode> inlineWatchChildren = rootNode.getInlineWatchChildren();
    int minIndex = Integer.MAX_VALUE;
    List<XDebuggerTreeNode> toRemove = new ArrayList<>();
    List<InlineWatchNode> toRemoveInlines = new ArrayList<>();
    for (XDebuggerTreeNode node : nodes) {

      if (node instanceof InlineWatchNode) {
        int index = inlineWatchChildren.indexOf(node);
        if (index != -1) {
          toRemoveInlines.add((InlineWatchNode)node);
          minIndex = Math.min(minIndex, index);
        }
      } else {
        int index = children.indexOf(node);
        if (index != -1) {
          toRemove.add(node);
          minIndex = Math.min(minIndex, index);
        }
      }
    }

    rootNode.removeChildren(toRemove);
    rootNode.removeInlineChildren(toRemoveInlines);


    List<? extends WatchNode> newChildren = myRootNode.getWatchChildren();
    if (!newChildren.isEmpty()) {
      WatchNode node = newChildren.get(Math.min(minIndex, newChildren.size() - 1));
      TreeUtil.selectNode(getTree(), node);
    }
    updateSessionData();

  }

  @Override
  public void removeInlineWatches(List<? extends XDebuggerTreeNode> nodes) {

  }

  @Override
  public void removeAllInlineWatches() {

  }

  @Override
  public void dispose() {
    Disposer.dispose(myDisposables);
    DnDManager.getInstance().unregisterTarget(this, getTree());
    super.dispose();
  }

  public void showInplaceEditor(XSourcePosition position, Editor mainEditor) {
    InlineWatchInplaceEditor inplaceEditor = new InlineWatchInplaceEditor(position, getTree(), mainEditor, this);
    inplaceEditor.show();
  }

}
