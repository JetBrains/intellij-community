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
package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionAdapter;
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
  @Nullable private final WatchNode myOldNode;

  public WatchInplaceEditor(@NotNull WatchesRootNode rootNode,
                            @NotNull XDebugSession session, XWatchesView watchesView, final WatchNode node,
                            @NonNls final String historyId,
                            final @Nullable WatchNode oldNode) {
    super((XDebuggerTreeNode)node, historyId);
    myRootNode = rootNode;
    myWatchesView = watchesView;
    myOldNode = oldNode;
    myExpressionEditor.setText(oldNode != null ? oldNode.getExpression() : "");
    new WatchEditorSessionListener(session).install();
  }

  protected JComponent createInplaceEditorComponent() {
    return myExpressionEditor.getComponent();
  }

  public void cancelEditing() {
    if (!isShown()) return;
    super.cancelEditing();
    int index = myRootNode.removeChildNode(getNode());
    if (myOldNode != null && index != -1) {
      myWatchesView.addWatchExpression(myOldNode.getExpression(), index, false);
    }
  }

  public void doOKAction() {
    String expression = myExpressionEditor.getText();
    myExpressionEditor.saveTextInHistory();
    super.doOKAction();
    int index = myRootNode.removeChildNode(getNode());
    if (!StringUtil.isEmpty(expression) && index != -1) {
      myWatchesView.addWatchExpression(expression, index, false);
    }
  }

  private class WatchEditorSessionListener extends XDebugSessionAdapter {
    private final XDebugSession mySession;

    public WatchEditorSessionListener(@NotNull XDebugSession session) {
      mySession = session;
    }

    public void install() {
      mySession.addSessionListener(this);
    }

    private void cancel() {
      mySession.removeSessionListener(this);
      AppUIUtil.invokeOnEdt(new Runnable() {
        @Override
        public void run() {
          cancelEditing();
        }
      });
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
