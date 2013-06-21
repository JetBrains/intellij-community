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

import com.intellij.ide.dnd.DnDManager;
import com.intellij.openapi.Disposable;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRestorer;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XStackFrameNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode.createInfoMessage;

/**
 * @author nik
 */
public class XVariablesView extends XDebugViewBase {
  private final XDebuggerTreePanel myDebuggerTreePanel;
  private XDebuggerTreeState myTreeState;
  private Object myFrameEqualityObject;
  private XDebuggerTreeRestorer myTreeRestorer;

  public XVariablesView(@NotNull XDebugSession session, final Disposable parentDisposable) {
    super(session, parentDisposable);
    XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    myDebuggerTreePanel = new XDebuggerTreePanel(session, editorsProvider, this, null, XDebuggerActions.VARIABLES_TREE_POPUP_GROUP);
    myDebuggerTreePanel.getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.variables.not.available"));
    DnDManager.getInstance().registerSource(myDebuggerTreePanel, myDebuggerTreePanel.getTree());
  }

  @Override
  protected void rebuildView(final SessionEvent event) {
    XStackFrame stackFrame = mySession.getCurrentStackFrame();
    XDebuggerTree tree = myDebuggerTreePanel.getTree();

    if (event == SessionEvent.BEFORE_RESUME || event == SessionEvent.SETTINGS_CHANGED) {
      disposeTreeRestorer();
      myFrameEqualityObject = stackFrame != null ? stackFrame.getEqualityObject() : null;
      myTreeState = XDebuggerTreeState.saveState(tree);
      if (event == SessionEvent.BEFORE_RESUME) {
        return;
      }
    }

    tree.markNodesObsolete();
    if (stackFrame != null) {
      tree.setSourcePosition(stackFrame.getSourcePosition());
      tree.setRoot(new XStackFrameNode(tree, stackFrame), false);
      Object newEqualityObject = stackFrame.getEqualityObject();
      if (myFrameEqualityObject != null && newEqualityObject != null && myFrameEqualityObject.equals(newEqualityObject) && myTreeState != null) {
        disposeTreeRestorer();
        myTreeRestorer = myTreeState.restoreState(tree);
      }
    }
    else {
      tree.setSourcePosition(null);
      XDebugProcess debugProcess = mySession.getDebugProcess();
      XDebuggerTreeNode node;
      if (!mySession.isStopped() && mySession.isPaused()) {
        node = createInfoMessage(tree, "Frame is not available");
      }
      else {
        node = createInfoMessage(tree, debugProcess.getCurrentStateMessage(), debugProcess.getCurrentStateHyperlinkListener());
      }
      tree.setRoot(node, true);
    }
  }

  private void disposeTreeRestorer() {
    if (myTreeRestorer != null) {
      myTreeRestorer.dispose();
      myTreeRestorer = null;
    }
  }

  public XDebuggerTree getTree() {
    return myDebuggerTreePanel.getTree();
  }

  public JComponent getPanel() {
    return myDebuggerTreePanel.getMainPanel();
  }

  @Override
  public void dispose() {
    disposeTreeRestorer();
    DnDManager.getInstance().unregisterSource(myDebuggerTreePanel, myDebuggerTreePanel.getTree());
    super.dispose();
  }
}
