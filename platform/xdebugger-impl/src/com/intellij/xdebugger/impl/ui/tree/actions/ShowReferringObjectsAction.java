/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XReferrersProvider;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
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
