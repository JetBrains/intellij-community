/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class XInspectDialog extends DialogWrapper {
  private final XDebuggerTreePanel myTreePanel;

  public XInspectDialog(final XDebugSession session, XDebuggerEditorsProvider editorsProvider, XSourcePosition sourcePosition, @NotNull String nodeName, @NotNull XValue value) {
    super(session.getProject(), false);
    setTitle(XDebuggerBundle.message("inspect.value.dialog.title", nodeName));
    setModal(false);
    myTreePanel = new XDebuggerTreePanel(session, editorsProvider, myDisposable, sourcePosition, XDebuggerActions.INSPECT_TREE_POPUP_GROUP);
    XDebuggerTree tree = myTreePanel.getTree();
    tree.setRoot(new XValueNodeImpl(tree, null, nodeName, value), true);
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myTreePanel.getMainPanel();
  }

  @Nullable
  protected JComponent createSouthPanel() {
    return null;
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "#xdebugger.XInspectDialog";
  }
}
