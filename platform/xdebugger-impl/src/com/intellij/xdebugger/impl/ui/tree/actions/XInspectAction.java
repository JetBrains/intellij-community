// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class XInspectAction extends XDebuggerTreeActionBase {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull final String nodeName, AnActionEvent e) {
    XDebuggerTree tree = node.getTree();
    XValue value = node.getValueContainer();
    XInspectDialog dialog = new XInspectDialog(tree.getProject(), tree.getEditorsProvider(), tree.getSourcePosition(), nodeName, value,
                                               tree.getValueMarkers(),
                                               DebuggerUIUtil.getSession(e),
                                               true);
    dialog.show();
  }
}
