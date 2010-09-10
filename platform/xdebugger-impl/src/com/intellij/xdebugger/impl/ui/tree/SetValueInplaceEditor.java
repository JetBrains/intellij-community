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

import com.intellij.openapi.ui.Messages;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class SetValueInplaceEditor extends XDebuggerTreeInplaceEditor {
  private final JPanel myEditorPanel;
  private final XValueModifier myModifier;
  private final XValueNodeImpl myValueNode;

  public SetValueInplaceEditor(final XValueNodeImpl node, @NotNull final String nodeName) {
    super(node, "setValue");
    myValueNode = node;
    myModifier = myValueNode.getValueContainer().getModifier();

    myEditorPanel = new JPanel();
    myEditorPanel.setLayout(new BoxLayout(myEditorPanel, BoxLayout.X_AXIS));
    SimpleColoredComponent nameLabel = new SimpleColoredComponent();
    nameLabel.setIcon(getNode().getIcon());
    nameLabel.append(nodeName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);

    myEditorPanel.add(nameLabel);

    myEditorPanel.add(myExpressionEditor.getComponent());
    final String value = myModifier != null ? myModifier.getInitialValueEditorText() : null;
    myExpressionEditor.setText(value != null ? value : "");
    myExpressionEditor.selectAll();
  }

  protected JComponent createInplaceEditorComponent() {
    return myEditorPanel;
  }

  public void doOKAction() {
    if (myModifier == null) return;
    
    myExpressionEditor.saveTextInHistory();
    final XDebuggerTreeState treeState = XDebuggerTreeState.saveState(myTree);
    myValueNode.setValueModificationStarted();
    myModifier.setValue(myExpressionEditor.getText(), new XValueModifier.XModificationCallback() {
      public void valueModified() {
        DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
          public void run() {
            myTree.rebuildAndRestore(treeState);
          }
        });
      }

      public void errorOccurred(@NotNull final String errorMessage) {
        DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
          public void run() {
            myTree.rebuildAndRestore(treeState);
            //todo[nik] show hint instead
            Messages.showErrorDialog(myTree, errorMessage);
          }
        });
      }
    });
    super.doOKAction();
  }
}
