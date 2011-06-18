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
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class XBreakpointPropertiesPanel<B extends XBreakpoint<?>> {
  private JPanel myMainPanel;

  private XSuspendPolicyPanel<B> mySuspendPolicyPanel;

  private JCheckBox myConditionCheckBox;
  private JPanel myConditionExpressionPanel;
  private JPanel myCustomConditionsPanelWrapper;
  private JPanel myCustomPropertiesPanelWrapper;
  private JPanel myConditionsPanel;

  private XMasterBreakpointPanel<B> myMasterBreakpointPanel;
  private XBreakpointActionsPanel<B> myBreakpointActionsPanel;

  private XDebuggerExpressionComboBox myConditionComboBox;

  private final List<XBreakpointCustomPropertiesPanel<B>> myCustomPanels;

  private final B myBreakpoint;

  public XBreakpointPropertiesPanel(Project project, final XBreakpointManager breakpointManager, @NotNull B breakpoint) {
    myBreakpoint = breakpoint;
    XBreakpointType<B, ?> breakpointType = XBreakpointUtil.getType(breakpoint);

    mySuspendPolicyPanel.init(project, breakpointManager, breakpoint);

    XDebuggerEditorsProvider debuggerEditorsProvider = breakpointType.getEditorsProvider();

    myBreakpointActionsPanel.init(project, breakpointManager, breakpoint, debuggerEditorsProvider);

    if (debuggerEditorsProvider != null) {
      ActionListener listener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          onCheckboxChanged();
        }
      };

      myConditionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, "breakpointCondition", myBreakpoint.getSourcePosition());
      JComponent conditionComponent = myConditionComboBox.getComponent();
      myConditionExpressionPanel.add(conditionComponent, BorderLayout.CENTER);
      myConditionComboBox.setEnabled(false);
      myConditionCheckBox.addActionListener(listener);
      DebuggerUIUtil.focusEditorOnCheck(myConditionCheckBox, conditionComponent);
    }
    else {
      myConditionCheckBox.setVisible(false);
    }

    myMasterBreakpointPanel.init(project, breakpointManager, breakpoint);

    myCustomPanels = new ArrayList<XBreakpointCustomPropertiesPanel<B>>();
    XBreakpointCustomPropertiesPanel<B> customConditionPanel = breakpointType.createCustomConditionsPanel();
    if (customConditionPanel != null) {
      myCustomConditionsPanelWrapper.add(customConditionPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customConditionPanel);
    }

    if (debuggerEditorsProvider == null && customConditionPanel == null) {
      myConditionsPanel.setVisible(false);
    }

    XBreakpointCustomPropertiesPanel<B> customPropertiesPanel = breakpointType.createCustomPropertiesPanel();
    if (customPropertiesPanel != null) {
      myCustomPropertiesPanelWrapper.add(customPropertiesPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customPropertiesPanel);
    }

    loadProperties();
  }

  private void onCheckboxChanged() {
    if (myConditionComboBox != null) {
      myConditionComboBox.setEnabled(myConditionCheckBox.isSelected());
    }
  }

  private void loadProperties() {
    mySuspendPolicyPanel.loadProperties();

    myBreakpointActionsPanel.loadProperties();

    myMasterBreakpointPanel.loadProperties();

    if (myConditionComboBox != null) {
      String condition = myBreakpoint.getCondition();
      myConditionCheckBox.setSelected(condition != null);
      myConditionComboBox.setText(condition != null ? condition : "");
    }

    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.loadFrom(myBreakpoint);
    }

    onCheckboxChanged();
  }

  public B getBreakpoint() {
    return myBreakpoint;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void saveProperties() {
    mySuspendPolicyPanel.saveProperties();

    myBreakpointActionsPanel.saveProperties();

    myMasterBreakpointPanel.saveProperties();

    if (myConditionComboBox != null) {
      String condition = myConditionCheckBox.isSelected() ? myConditionComboBox.getText() : null;
      myBreakpoint.setCondition(condition);
      myConditionComboBox.saveTextInHistory();
    }

    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.saveTo(myBreakpoint);
    }
    if (!myCustomPanels.isEmpty()) {
      ((XBreakpointBase)myBreakpoint).fireBreakpointChanged();
    }
  }


  public void dispose() {
    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.dispose();
    }
  }

}
