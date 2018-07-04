/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class XBreakpointActionsPanel extends XBreakpointPropertiesSubPanel {
  public static final String LOG_EXPRESSION_HISTORY_ID = "breakpointLogExpression";

  private JCheckBox myLogMessageCheckBox;
  private JCheckBox myLogExpressionCheckBox;
  private JPanel myLogExpressionPanel;
  private JPanel myContentPane;
  private JPanel myMainPanel;
  private JCheckBox myTemporaryCheckBox;
  private JPanel myExpressionPanel;
  private JPanel myLanguageChooserPanel;
  private JCheckBox myLogStack;
  private XDebuggerExpressionComboBox myLogExpressionComboBox;

  public void init(Project project, XBreakpointManager breakpointManager, @NotNull XBreakpointBase breakpoint, @Nullable XDebuggerEditorsProvider debuggerEditorsProvider) {
    init(project, breakpointManager, breakpoint);
    if (debuggerEditorsProvider != null) {
      ActionListener listener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          onCheckboxChanged();
        }
      };
      myLogExpressionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, LOG_EXPRESSION_HISTORY_ID,
                                                                myBreakpoint.getSourcePosition(), true, false);
      myLanguageChooserPanel.add(myLogExpressionComboBox.getLanguageChooser(), BorderLayout.CENTER);
      myLogExpressionPanel.setBorder(JBUI.Borders.emptyLeft(UIUtil.getCheckBoxTextHorizontalOffset(myLogExpressionCheckBox)));
      myLogExpressionPanel.add(myLogExpressionComboBox.getComponent(), BorderLayout.CENTER);
      myLogExpressionComboBox.setEnabled(false);
      boolean isLineBreakpoint = breakpoint instanceof XLineBreakpoint;
      myTemporaryCheckBox.setVisible(isLineBreakpoint);
      if (isLineBreakpoint) {
        myTemporaryCheckBox.addActionListener(e -> ((XLineBreakpoint)myBreakpoint).setTemporary(myTemporaryCheckBox.isSelected()));
      }
      myLogExpressionCheckBox.addActionListener(listener);
      DebuggerUIUtil.focusEditorOnCheck(myLogExpressionCheckBox, myLogExpressionComboBox.getEditorComponent());
    }
    else {
      myExpressionPanel.getParent().remove(myExpressionPanel);
    }
  }

  @Override
  public boolean lightVariant(boolean showAllOptions) {
    if (!showAllOptions && !myBreakpoint.isLogMessage() && !myBreakpoint.isLogStack() && myBreakpoint.getLogExpression() == null &&
        (!(myBreakpoint instanceof XLineBreakpoint) || !((XLineBreakpoint)myBreakpoint).isTemporary()) ) {
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
    myLogStack.setSelected(myBreakpoint.isLogStack());

    if (myBreakpoint instanceof XLineBreakpoint) {
      myTemporaryCheckBox.setSelected(((XLineBreakpoint)myBreakpoint).isTemporary());
    }

    if (myLogExpressionComboBox != null) {
      XExpression logExpression = myBreakpoint.getLogExpressionObjectInt();
      myLogExpressionComboBox.setExpression(logExpression);
      myLogExpressionCheckBox.setSelected(myBreakpoint.isLogExpressionEnabled() && logExpression != null);
    }
    onCheckboxChanged();
  }

  @Override
  void saveProperties() {
    myBreakpoint.setLogMessage(myLogMessageCheckBox.isSelected());
    myBreakpoint.setLogStack(myLogStack.isSelected());

    if (myBreakpoint instanceof XLineBreakpoint) {
      ((XLineBreakpoint)myBreakpoint).setTemporary(myTemporaryCheckBox.isSelected());
    }

    if (myLogExpressionComboBox != null) {
      XExpression expression = myLogExpressionComboBox.getExpression();
      XExpression logExpression = !XDebuggerUtilImpl.isEmptyExpression(expression) ? expression : null;
      myBreakpoint.setLogExpressionEnabled(logExpression == null || myLogExpressionCheckBox.isSelected());
      myBreakpoint.setLogExpressionObject(logExpression);
      myLogExpressionComboBox.saveTextInHistory();
    }
  }

  JComponent getDefaultFocusComponent() {
    if (myLogExpressionComboBox != null && myLogExpressionComboBox.getComboBox().isEnabled()) {
      return myLogExpressionComboBox.getEditorComponent();
    }
    return null;
  }

  public void dispose() {
  }

  public void hide() {
    myContentPane.setVisible(false);
  }
}
