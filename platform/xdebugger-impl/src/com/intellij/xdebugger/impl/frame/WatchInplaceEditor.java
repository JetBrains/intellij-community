/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.frame;

import com.intellij.ui.AppUIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class WatchInplaceEditor extends XDebuggerTreeInplaceEditor {
  private final WatchesRootNode myRootNode;
  private final XWatchesView myWatchesView;
  private final WatchNode myOldNode;
  private WatchEditorSessionListener mySessionListener;

  public WatchInplaceEditor(@NotNull WatchesRootNode rootNode,
                            @Nullable XDebugSession session,
                            XWatchesView watchesView,
                            WatchNode node,
                            @NonNls String historyId,
                            @Nullable WatchNode oldNode) {
    super((XDebuggerTreeNode)node, historyId);
    myRootNode = rootNode;
    myWatchesView = watchesView;
    myOldNode = oldNode;
    myExpressionEditor.setExpression(oldNode != null ? oldNode.getExpression() : null);
    if (session != null) {
      mySessionListener = new WatchEditorSessionListener(session).install();
    }
  }

  @Override
  protected JComponent createInplaceEditorComponent() {
    return myExpressionEditor.getComponent();
  }

  @Override
  public void cancelEditing() {
    if (!isShown()) return;
    super.cancelEditing();
    int index = myRootNode.getIndex(getNode());
    if (myOldNode == null && index != -1) {
      myRootNode.removeChildNode(getNode());
    }
    TreeUtil.selectNode(myTree, getNode());
  }

  @Override
  public void doOKAction() {
    XExpression expression = myExpressionEditor.getExpression();
    myExpressionEditor.saveTextInHistory();
    super.doOKAction();
    int index = myRootNode.removeChildNode(getNode());
    if (!XDebuggerUtilImpl.isEmptyExpression(expression) && index != -1) {
      myWatchesView.addWatchExpression(expression, index, false);
    }
    TreeUtil.selectNode(myTree, getNode());
  }

  @Override
  protected void onHidden() {
    super.onHidden();
    if (mySessionListener != null) {
      mySessionListener.remove();
    }
  }

  private class WatchEditorSessionListener implements XDebugSessionListener {
    private final XDebugSession mySession;

    public WatchEditorSessionListener(@NotNull XDebugSession session) {
      mySession = session;
    }

    public WatchEditorSessionListener install() {
      mySession.addSessionListener(this);
      return this;
    }

    public void remove() {
      mySession.removeSessionListener(this);
    }

    private void cancel() {
      AppUIUtil.invokeOnEdt(WatchInplaceEditor.this::cancelEditing);
    }

    @Override
    public void sessionPaused() {
      cancel();
    }

    @Override
    public void beforeSessionResume() {
      cancel();
    }

    @Override
    public void sessionResumed() {
      cancel();
    }

    @Override
    public void sessionStopped() {
      cancel();
    }
  }
}
