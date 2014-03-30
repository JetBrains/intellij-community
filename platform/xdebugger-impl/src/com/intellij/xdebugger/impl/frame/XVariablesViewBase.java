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

import com.intellij.ide.dnd.DnDManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRestorer;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XStackFrameNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class XVariablesViewBase implements Disposable {
  protected final XDebuggerTreePanel myDebuggerTreePanel;
  private XDebuggerTreeState myTreeState;
  private Object myFrameEqualityObject;
  private XDebuggerTreeRestorer myTreeRestorer;

  protected XVariablesViewBase(@NotNull Project project, @NotNull XDebuggerEditorsProvider editorsProvider, @Nullable XValueMarkers<?, ?> markers) {
    myDebuggerTreePanel = new XDebuggerTreePanel(project, editorsProvider, this, null, XDebuggerActions.VARIABLES_TREE_POPUP_GROUP, markers);
    myDebuggerTreePanel.getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.variables.not.available"));
    DnDManager.getInstance().registerSource(myDebuggerTreePanel, myDebuggerTreePanel.getTree());
  }

  protected void buildTreeAndRestoreState(@NotNull XStackFrame stackFrame) {
    XDebuggerTree tree = myDebuggerTreePanel.getTree();
    tree.setSourcePosition(stackFrame.getSourcePosition());
    tree.setRoot(new XStackFrameNode(tree, stackFrame), false);
    Object newEqualityObject = stackFrame.getEqualityObject();
    if (myFrameEqualityObject != null && newEqualityObject != null && myFrameEqualityObject.equals(newEqualityObject)
        && myTreeState != null) {
      disposeTreeRestorer();
      myTreeRestorer = myTreeState.restoreState(tree);
    }
  }

  protected void saveCurrentTreeState(@Nullable XStackFrame stackFrame) {
    disposeTreeRestorer();
    myFrameEqualityObject = stackFrame != null ? stackFrame.getEqualityObject() : null;
    myTreeState = XDebuggerTreeState.saveState(myDebuggerTreePanel.getTree());
  }

  private void disposeTreeRestorer() {
    if (myTreeRestorer != null) {
      myTreeRestorer.dispose();
      myTreeRestorer = null;
    }
  }

  public XDebuggerTree getTree() {
    return myDebuggerTreePanel.getTree();
  }

  public JComponent getPanel() {
    return myDebuggerTreePanel.getMainPanel();
  }

  @Override
  public void dispose() {
    disposeTreeRestorer();
    DnDManager.getInstance().unregisterSource(myDebuggerTreePanel, myDebuggerTreePanel.getTree());
  }
}
