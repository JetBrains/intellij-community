// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XReferrersProvider;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class ShowReferringObjectsAction extends XDebuggerTreeActionBase {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    presentation.setVisible(presentation.isEnabled());
  }

  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    return node.getValueContainer().getReferrersProvider() != null;
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    XReferrersProvider referrersProvider = node.getValueContainer().getReferrersProvider();
    if (referrersProvider != null) {
      XDebuggerTree tree = node.getTree();
      XDebugSession session = DebuggerUIUtil.getSession(e);
      if (session != null) {
        XValue referringObjectsRoot = referrersProvider.getReferringObjectsValue();
        XInspectDialog dialog = new XInspectDialog(tree.getProject(),
                                                   tree.getEditorsProvider(),
                                                   tree.getSourcePosition(),
                                                   nodeName,
                                                   referringObjectsRoot,
                                                   tree.getValueMarkers(), session, false);
        XDebuggerTree debuggerTree = dialog.getTree();
        if (referringObjectsRoot instanceof ReferrersTreeCustomizer) {
          ((ReferrersTreeCustomizer)referringObjectsRoot).customizeTree(debuggerTree);
        }

        dialog.setTitle(XDebuggerBundle.message("showReferring.dialog.title", nodeName));
        dialog.show();
      }
    }
  }

  /**
   * Implement this interface by referring objects root to customize debugger tree.
   */
  @ApiStatus.Experimental
  public interface ReferrersTreeCustomizer {
    void customizeTree(XDebuggerTree referrersTree);
  }
}
