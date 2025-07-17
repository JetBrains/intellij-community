// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XReferrersProvider;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.util.MonolithUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowReferringObjectsAction extends XDebuggerTreeActionBase
  implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {

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
    XDebugSessionProxy session = DebuggerUIUtil.getSessionProxy(e);
    if (session == null) {
      return;
    }
    XValue xValue = node.getValueContainer();
    var dialog = createReferringObjectsDialog(xValue, session, nodeName, position, markers);
    if (dialog != null) {
      dialog.show();
    }
  }

  private static @Nullable DialogWrapper createReferringObjectsDialog(
    XValue xValue,
    XDebugSessionProxy session,
    @NotNull String nodeName,
    XSourcePosition position,
    XValueMarkers<?, ?> markers
  ) {
    XReferrersProvider referrersProvider = xValue.getReferrersProvider();
    if (referrersProvider != null) {
      XValue referringObjectsRoot = referrersProvider.getReferringObjectsValue();
      DialogWrapper dialog;
      // TODO ReferrersTreeCustomizer is supported only in monolith
      XDebugSession xDebugSession = MonolithUtils.findSessionById(session.getId());
      if (xDebugSession != null && referringObjectsRoot instanceof ReferrersTreeCustomizer referrersTreeCustomizer) {
        dialog = referrersTreeCustomizer.getDialog(xDebugSession, nodeName, position, markers);
      }
      else {
        dialog = new XInspectDialog(session.getProject(),
                                    session.getEditorsProvider(),
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
