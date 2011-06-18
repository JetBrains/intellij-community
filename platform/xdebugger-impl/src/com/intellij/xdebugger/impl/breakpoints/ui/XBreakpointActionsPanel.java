/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by IntelliJ IDEA.
 * User: zajac
 * Date: 18.06.11
 * Time: 9:45
 * To change this template use File | Settings | File Templates.
 */
public class XBreakpointActionsPanel<B extends XBreakpoint<?>> extends XBreakpointPropertiesSubPanel<B> {
  private JCheckBox myLogMessageCheckBox;
  private JCheckBox myLogExpressionCheckBox;
  private JPanel myLogExpressionPanel;
  private JPanel myContentPane;
  private JPanel myMainPanel;
  private XDebuggerExpressionComboBox myLogExpressionComboBox;

  public void init(Project project, XBreakpointManager breakpointManager, @NotNull B breakpoint, @Nullable XDebuggerEditorsProvider debuggerEditorsProvider) {
    init(project, breakpointManager, breakpoint);
    if (debuggerEditorsProvider != null) {
      ActionListener listener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          onCheckboxChanged();
        }
      };

      myLogExpressionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, "breakpointLogExpression", myBreakpoint.getSourcePosition());
      JComponent logExpressionComponent = myLogExpressionComboBox.getComponent();
      myLogExpressionPanel.add(logExpressionComponent, BorderLayout.CENTER);
      myLogExpressionComboBox.setEnabled(false);
      myLogExpressionCheckBox.addActionListener(listener);
      DebuggerUIUtil.focusEditorOnCheck(myLogExpressionCheckBox, logExpressionComponent);
    }
    else {
      myLogExpressionCheckBox.setVisible(false);
    }
  }

  @Override
  public boolean lightVariant(boolean showAllOptions) {
    if (!showAllOptions && !myBreakpoint.isLogMessage() && myBreakpoint.getLogExpression() == null) {
      myMainPanel.setVisible(false);
      return true;
    } else {
      myMainPanel.setBorder(null);
      return false;
    }
  }

  private void onCheckboxChanged() {
    if (myLogExpressionComboBox != null) {
      myLogExpressionComboBox.setEnabled(myLogExpressionCheckBox.isSelected());
    }
  }

  @Override
  void loadProperties() {
    myLogMessageCheckBox.setSelected(myBreakpoint.isLogMessage());
    if (myLogExpressionComboBox != null) {
      String logExpression = myBreakpoint.getLogExpression();
      myLogExpressionCheckBox.setSelected(logExpression != null);
      myLogExpressionComboBox.setText(logExpression != null ? logExpression : "");
    }
    onCheckboxChanged();
  }

  @Override
  void saveProperties() {
    myBreakpoint.setLogMessage(myLogMessageCheckBox.isSelected());

    if (myLogExpressionComboBox != null) {
      String logExpression = myLogExpressionCheckBox.isSelected() ? myLogExpressionComboBox.getText() : null;
      myBreakpoint.setLogExpression(logExpression);
      myLogExpressionComboBox.saveTextInHistory();
    }
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
