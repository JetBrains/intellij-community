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

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValuePresentationUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

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
    myEditorPanel.setLayout(new BorderLayout(0, 0));
    SimpleColoredComponent nameLabel = new SimpleColoredComponent();
    nameLabel.setIcon(getNode().getIcon());
    nameLabel.append(nodeName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
    XValuePresentation presentation = node.getValuePresentation();
    if (presentation != null) {
      XValuePresentationUtil.appendSeparator(nameLabel, presentation.getSeparator());
    }
    myEditorPanel.add(nameLabel, BorderLayout.WEST);

    myEditorPanel.add(myExpressionEditor.getComponent(), BorderLayout.CENTER);
    final String value = myModifier != null ? myModifier.getInitialValueEditorText() : null;
    myExpressionEditor.setText(value != null ? value : "");
    myExpressionEditor.selectAll();
  }

  @Override
  protected JComponent createInplaceEditorComponent() {
    return myEditorPanel;
  }

  @Override
  public void doOKAction() {
    if (myModifier == null) return;

    myExpressionEditor.saveTextInHistory();
    final XDebuggerTreeState treeState = XDebuggerTreeState.saveState(myTree);
    myValueNode.setValueModificationStarted();
    myModifier.setValue(myExpressionEditor.getText(), new XValueModifier.XModificationCallback() {
      @Override
      public void valueModified() {
        AppUIUtil.invokeOnEdt(new Runnable() {
          @Override
          public void run() {
            myTree.rebuildAndRestore(treeState);
          }
        });
      }

      @Override
      public void errorOccurred(@NotNull final String errorMessage) {
        AppUIUtil.invokeOnEdt(new Runnable() {
          @Override
          public void run() {
            myTree.rebuildAndRestore(treeState);

            Editor editor = myExpressionEditor.getEditor();
            if (editor != null) {
              HintManager.getInstance().showErrorHint(editor, errorMessage);
            }
            else {
              Messages.showErrorDialog(myTree, errorMessage);
            }
          }
        });
      }
    });
    super.doOKAction();
  }
}
