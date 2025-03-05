// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XReferrersProvider;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowReferringObjectsAction extends XDebuggerTreeActionBase {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

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
    XDebuggerTree tree = node.getTree();
    XSourcePosition position = tree.getSourcePosition();
    XValueMarkers<?, ?> markers = tree.getValueMarkers();
    XDebugSession session = DebuggerUIUtil.getSession(e);
    if (session == null) {
      return;
    }
    XValue xValue = node.getValueContainer();
    var dialog = createReferringObjectsDialog(xValue, session, nodeName, position, markers);
    if (dialog != null) {
      dialog.show();
    }
  }

  @ApiStatus.Internal
  public static @Nullable DialogWrapper createReferringObjectsDialog(
    XValue xValue,
    XDebugSession session,
    @NotNull String nodeName,
    XSourcePosition position,
    XValueMarkers<?, ?> markers
  ) {
    XReferrersProvider referrersProvider = xValue.getReferrersProvider();
    if (referrersProvider != null) {
      XValue referringObjectsRoot = referrersProvider.getReferringObjectsValue();
      DialogWrapper dialog;
      if (referringObjectsRoot instanceof ReferrersTreeCustomizer referrersTreeCustomizer) {
        dialog = referrersTreeCustomizer.getDialog(session, nodeName, position, markers);
      }
      else {
        dialog = new XInspectDialog(session.getProject(),
                                    session.getDebugProcess().getEditorsProvider(),
                                    position,
                                    nodeName,
                                    referringObjectsRoot,
                                    markers, session, false);
        dialog.setTitle(XDebuggerBundle.message("showReferring.dialog.title", nodeName));
      }
      return dialog;
    }
    return null;
  }

  /**
   * Implement this interface by referring objects root to customize debugger tree.
   */
  @ApiStatus.Experimental
  public interface ReferrersTreeCustomizer {
    DialogWrapper getDialog(XDebugSession session, String nodeName, XSourcePosition position, XValueMarkers<?, ?> markers);
  }
}
