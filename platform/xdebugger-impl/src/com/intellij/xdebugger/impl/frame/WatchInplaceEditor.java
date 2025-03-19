// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame;

import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XDebuggerWatchesManager;
import com.intellij.xdebugger.impl.inline.InlineWatch;
import com.intellij.xdebugger.impl.inline.InlineWatchNodeImpl;
import com.intellij.xdebugger.impl.inline.XInlineWatchesView;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;

@ApiStatus.Internal
public class WatchInplaceEditor extends XDebuggerTreeInplaceEditor {
  private final WatchesRootNode myRootNode;
  private final XWatchesView myWatchesView;
  private final WatchNode myOldNode;

  public WatchInplaceEditor(@NotNull WatchesRootNode rootNode,
                            XWatchesView watchesView,
                            WatchNode node,
                            @Nullable WatchNode oldNode) {
    super((XDebuggerTreeNode)node, "watch");
    myRootNode = rootNode;
    myWatchesView = watchesView;
    myOldNode = oldNode;
    myExpressionEditor.setExpression(oldNode != null ? oldNode.getExpression() : null);
  }

  @Override
  public void cancelEditing() {
    if (!isShown()) return;
    super.cancelEditing();
    int index = myRootNode.getIndex(myNode);
    if (myOldNode == null && index != -1) {
      myRootNode.removeChildNode(myNode);
    }
    TreeUtil.selectNode(myTree, myNode);
  }

  @Override
  public void doOKAction() {
    XExpression expression = getExpression();
    super.doOKAction();
    int index = myRootNode.removeChildNode(myNode);
    XDebuggerWatchesManager watchesManager = null;
    if (myNode instanceof InlineWatchNodeImpl) {
      InlineWatch inlineWatch = ((InlineWatchNodeImpl)myNode).getWatch();
      watchesManager = ((XDebuggerManagerImpl)XDebuggerManager.getInstance(getProject())).getWatchesManager();
      watchesManager.inlineWatchesRemoved(Collections.singletonList(inlineWatch), (XInlineWatchesView)myWatchesView);
    }
    if (!XDebuggerUtilImpl.isEmptyExpression(expression) && index != -1) {
      if (myNode instanceof InlineWatchNodeImpl) {
        watchesManager.addInlineWatchExpression(expression, index, ((InlineWatchNodeImpl)myNode).getPosition(), false);
      } else  {
        myWatchesView.addWatchExpression(expression, index, false);
      }
    }
    TreeUtil.selectNode(myTree, myNode);
  }

  @Override
  protected @Nullable Rectangle getEditorBounds() {
    Rectangle bounds = super.getEditorBounds();
    if (bounds == null) {
      return null;
    }
    int afterIconX = getAfterIconX();
    bounds.x += afterIconX;
    bounds.width -= afterIconX;
    return bounds;
  }
}
