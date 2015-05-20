/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ObjectLongHashMap;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

import static com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode.createInfoMessage;

/**
 * @author nik
 */
public class XVariablesView extends XVariablesViewBase {
  public static final Key<Map<Pair<VirtualFile, Integer>, Set<XValueNodeImpl>>> DEBUG_VARIABLES = Key.create("debug.variables");
  public static final Key<ObjectLongHashMap<VirtualFile>> DEBUG_VARIABLES_TIMESTAMPS = Key.create("debug.variables.timestamps");

  public XVariablesView(@NotNull XDebugSessionImpl session) {
    super(session.getProject(), session.getDebugProcess().getEditorsProvider(), session.getValueMarkers());
  }

  @Override
  public void processSessionEvent(@NotNull final SessionEvent event) {
    XDebugSession session = getSession(getPanel());
    XStackFrame stackFrame = session == null ? null : session.getCurrentStackFrame();
    XDebuggerTree tree = getTree();

    if (event == SessionEvent.BEFORE_RESUME || event == SessionEvent.SETTINGS_CHANGED) {
      saveCurrentTreeState(stackFrame);
      if (event == SessionEvent.BEFORE_RESUME) {
        return;
      }
    }

    tree.markNodesObsolete();
    if (stackFrame != null) {
      cancelClear();
      buildTreeAndRestoreState(stackFrame);
    }
    else {
      requestClear();
    }
  }

  @Override
  public void dispose() {
    clearInlineData(getTree());
    super.dispose();
  }

  private static void clearInlineData(XDebuggerTree tree) {
    tree.getProject().putUserData(DEBUG_VARIABLES, null);
    tree.getProject().putUserData(DEBUG_VARIABLES_TIMESTAMPS, null);
    tree.updateEditor();
  }

  @Override
  protected void clear() {
    XDebuggerTree tree = getTree();
    tree.setSourcePosition(null);
    clearInlineData(tree);

    XDebuggerTreeNode node;
    XDebugSession session = getSession(getPanel());
    if (session == null || (!session.isStopped() && session.isPaused())) {
      node = createInfoMessage(tree, "Frame is not available");
    }
    else {
      XDebugProcess debugProcess = session.getDebugProcess();
      node = createInfoMessage(tree, debugProcess.getCurrentStateMessage(), debugProcess.getCurrentStateHyperlinkListener());
    }
    tree.setRoot(node, true);
  }
}
