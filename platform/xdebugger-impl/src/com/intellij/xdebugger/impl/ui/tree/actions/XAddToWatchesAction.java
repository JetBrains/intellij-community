/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
 * This action works only in the variables view, it is not generic action like {@see com.intellij.xdebugger.impl.actions.AddToWatchesAction}
 */
class XAddToWatchesAction extends XDebuggerTreeActionBase {
  @Override
  protected boolean isEnabled(@NotNull final XValueNodeImpl node, @NotNull AnActionEvent e) {
    return super.isEnabled(node, e) && DebuggerUIUtil.hasEvaluationExpression(node.getValueContainer()) && getWatchesView(e) != null;
  }

  @Override
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    final XWatchesView watchesView = getWatchesView(e);
    if (watchesView != null) {
      node.getValueContainer().calculateEvaluationExpression()
        .done(expression -> {
        if (expression != null) {
          watchesView.addWatchExpression(expression, -1, true);
        }
      });
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