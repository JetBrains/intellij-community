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
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
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
  private JCheckBox myTemporaryCheckBox;
  XBreakpointCustomPropertiesPanel<B> logExpressionPanel;

  public void init(Project project, XBreakpointManager breakpointManager, @NotNull B breakpoint, @Nullable XDebuggerEditorsProvider debuggerEditorsProvider) {
    init(project, breakpointManager, breakpoint);
    if (debuggerEditorsProvider != null) {
      ActionListener listener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          onCheckboxChanged();
        }
      };

      if (debuggerEditorsProvider instanceof XDebuggerComboBoxProvider) {
        logExpressionPanel = ((XDebuggerComboBoxProvider<B>)debuggerEditorsProvider).createLogExpressionComboBoxPanel(
          project, debuggerEditorsProvider, "breakpointCondition", myBreakpoint.getSourcePosition());
      }
      else {
        logExpressionPanel =
          new DefaultLogExpressionComboBoxPanel<B>(project, debuggerEditorsProvider, "breakpointCondition", myBreakpoint.getSourcePosition());
      }

      JComponent logExpressionComponent = logExpressionPanel.getComponent();
      myLogExpressionPanel.add(logExpressionComponent, BorderLayout.CENTER);
      logExpressionComponent.setEnabled(false);
      myTemporaryCheckBox.setVisible(breakpoint instanceof XLineBreakpoint);
      myLogExpressionCheckBox.addActionListener(listener);
      DebuggerUIUtil.focusEditorOnCheck(myLogExpressionCheckBox, logExpressionComponent);
    }
    else {
      myLogExpressionCheckBox.setVisible(false);
    }
  }

  @Override
  public boolean lightVariant(boolean showAllOptions) {
    if (!showAllOptions && !myBreakpoint.isLogMessage() && myBreakpoint.getLogExpression() == null &&
        (!(myBreakpoint instanceof XLineBreakpoint) || !((XLineBreakpoint)myBreakpoint).isTemporary()) ) {
      myMainPanel.setVisible(false);
      return true;
    } else {
      myMainPanel.setBorder(null);
      return false;
    }
  }

  private void onCheckboxChanged() {
    if (logExpressionPanel != null) {
      logExpressionPanel.getComponent().setEnabled(myLogExpressionCheckBox.isSelected());
    }
  }

  @Override
  void loadProperties() {
    myLogMessageCheckBox.setSelected(myBreakpoint.isLogMessage());

    if (myBreakpoint instanceof XLineBreakpoint) {
      myTemporaryCheckBox.setSelected(((XLineBreakpoint)myBreakpoint).isTemporary());
    }

    if (logExpressionPanel != null) {
      myLogExpressionCheckBox.setSelected(myBreakpoint.getLogExpression() != null);
      logExpressionPanel.loadFrom(myBreakpoint);
    }
    onCheckboxChanged();
  }

  @Override
  void saveProperties() {
    myBreakpoint.setLogMessage(myLogMessageCheckBox.isSelected());

    if (myBreakpoint instanceof XLineBreakpoint) {
      ((XLineBreakpoint)myBreakpoint).setTemporary(myTemporaryCheckBox.isSelected());
    }

    if (logExpressionPanel != null) {
      logExpressionPanel.saveTo(myBreakpoint);
    }
  }

  public void dispose() {
    if (logExpressionPanel != null) {
      logExpressionPanel.dispose();
    }
  }
}
