// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XReferrersProvider;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

public class ShowReferringObjectsAction extends XDebuggerTreeActionBase {

  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    return node.getValueContainer().getReferrersProvider() != null;
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    XReferrersProvider referrersProvider = node.getValueContainer().getReferrersProvider();
    if (referrersProvider != null) {
      XDebuggerTree tree = node.getTree();
      XDebugSession session = XDebuggerManager.getInstance(tree.getProject()).getCurrentSession();
      if (session != null) {
        XInspectDialog dialog = new XInspectDialog(tree.getProject(),
                                                   tree.getEditorsProvider(),
                                                   tree.getSourcePosition(),
                                                   nodeName,
                                                   referrersProvider.getReferringObjectsValue(),
                                                   tree.getValueMarkers(), session, false);
        dialog.setTitle(XDebuggerBundle.message("showReferring.dialog.title", nodeName));
        dialog.show();
      }
    }
  }
}
