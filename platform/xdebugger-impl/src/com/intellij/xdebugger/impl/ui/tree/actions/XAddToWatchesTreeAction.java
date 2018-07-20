// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

/**
 * This action works only in the variables view
 * @see com.intellij.xdebugger.impl.actions.AddToWatchesAction
 */
public class XAddToWatchesTreeAction extends XDebuggerTreeActionBase {
  @Override
  protected boolean isEnabled(@NotNull final XValueNodeImpl node, @NotNull AnActionEvent e) {
    return super.isEnabled(node, e) && DebuggerUIUtil.hasEvaluationExpression(node.getValueContainer()) && getWatchesView(e) != null;
  }

  @Override
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    final XWatchesView watchesView = getWatchesView(e);
    if (watchesView != null) {
      DebuggerUIUtil.addToWatches(watchesView, node);
    }
  }

  private static XWatchesView getWatchesView(@NotNull AnActionEvent e) {
    XWatchesView view = e.getData(XWatchesView.DATA_KEY);
    Project project = e.getProject();
    if (view == null && project != null) {
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session != null) {
        XDebugSessionTab tab = ((XDebugSessionImpl)session).getSessionTab();
        if (tab != null) {
          return tab.getWatchesView();
        }
      }
    }
    return view;
  }
}