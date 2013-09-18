/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XStackFrameNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class XStandaloneVariablesView implements Disposable {
  private final XDebuggerTreePanel myDebuggerTreePanel;

  public XStandaloneVariablesView(@NotNull Project project, @NotNull XDebuggerEditorsProvider editorsProvider) {
    myDebuggerTreePanel = new XDebuggerTreePanel(project, editorsProvider, this, null, XDebuggerActions.VARIABLES_TREE_POPUP_GROUP, null);
    getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.variables.not.available"));
  }

  public void showVariables(@NotNull XStackFrame stackFrame) {
    getTree().setSourcePosition(stackFrame.getSourcePosition());
    getTree().setRoot(new XStackFrameNode(getTree(), stackFrame), false);
  }

  public void showMessage(@NotNull String message) {
    getTree().setSourcePosition(null);
    getTree().setRoot(MessageTreeNode.createInfoMessage(getTree(), message), true);
  }

  private XDebuggerTree getTree() {
    return myDebuggerTreePanel.getTree();
  }

  @Override
  public void dispose() {

  }

  public JComponent getPanel() {
    return myDebuggerTreePanel.getMainPanel();
  }
}
