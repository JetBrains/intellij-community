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

import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NotNull;

import static com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode.createInfoMessage;

/**
 * @author nik
 */
public class XVariablesView extends XVariablesViewBase implements XDebugView {
  @NotNull private final XDebugSession mySession;

  public XVariablesView(@NotNull XDebugSession session) {
    super(session.getProject(), session.getDebugProcess().getEditorsProvider(), ((XDebugSessionImpl)session).getValueMarkers());
    mySession = session;
  }

  @Override
  public void processSessionEvent(@NotNull final SessionEvent event) {
    XStackFrame stackFrame = mySession.getCurrentStackFrame();
    XDebuggerTree tree = getTree();

    if (event == SessionEvent.BEFORE_RESUME || event == SessionEvent.SETTINGS_CHANGED) {
      saveCurrentTreeState(stackFrame);
      if (event == SessionEvent.BEFORE_RESUME) {
        return;
      }
    }

    tree.markNodesObsolete();
    if (stackFrame != null) {
      buildTreeAndRestoreState(stackFrame);
    }
    else {
      tree.setSourcePosition(null);

      XDebuggerTreeNode node;
      if (!mySession.isStopped() && mySession.isPaused()) {
        node = createInfoMessage(tree, "Frame is not available");
      }
      else {
        XDebugProcess debugProcess = mySession.getDebugProcess();
        node = createInfoMessage(tree, debugProcess.getCurrentStateMessage(), debugProcess.getCurrentStateHyperlinkListener());
      }
      tree.setRoot(node, true);
    }
  }
}
