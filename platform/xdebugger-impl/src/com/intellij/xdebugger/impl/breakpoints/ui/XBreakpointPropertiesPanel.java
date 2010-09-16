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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import com.intellij.ui.GuiUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class XBreakpointPropertiesPanel<B extends XBreakpoint<?>> {
  private JPanel myMainPanel;
  private JCheckBox mySuspendCheckBox;
  private JRadioButton mySuspendAllRadioButton;
  private JRadioButton mySuspendThreadRadioButton;
  private JRadioButton mySuspendNoneRadioButton;
  private JCheckBox myLogMessageCheckBox;
  private JCheckBox myLogExpressionCheckBox;
  private JPanel myLogExpressionPanel;
  private JCheckBox myConditionCheckBox;
  private JPanel myConditionExpressionPanel;
  private JPanel myCustomConditionsPanelWrapper;
  private JPanel mySuspendPolicyPanel;
  private JPanel myCustomPropertiesPanelWrapper;
  private JRadioButton myLeaveEnabledRadioButton;
  private JPanel myMasterBreakpointComboBoxPanel;
  private JPanel myAfterBreakpointHitPanel;
  private JPanel myConditionsPanel;
  private final XBreakpointManager myBreakpointManager;
  private final B myBreakpoint;
  private final Map<SuspendPolicy, JRadioButton> mySuspendRadioButtons;
  private final List<XBreakpointCustomPropertiesPanel<B>> myCustomPanels;
  private XDebuggerExpressionComboBox myLogExpressionComboBox;
  private XDebuggerExpressionComboBox myConditionComboBox;
  private final ComboBox myMasterBreakpointComboBox;
  private final XDependentBreakpointManager myDependentBreakpointManager;

  public XBreakpointPropertiesPanel(Project project, final XBreakpointManager breakpointManager, @NotNull B breakpoint) {
    myBreakpointManager = breakpointManager;
    myDependentBreakpointManager = ((XBreakpointManagerImpl)breakpointManager).getDependentBreakpointManager();
    myBreakpoint = breakpoint;

    XBreakpointType<B,?> type = XBreakpointUtil.getType(breakpoint);

    mySuspendRadioButtons = new HashMap<SuspendPolicy, JRadioButton>();
    mySuspendRadioButtons.put(SuspendPolicy.ALL, mySuspendAllRadioButton);
    mySuspendRadioButtons.put(SuspendPolicy.THREAD, mySuspendThreadRadioButton);
    mySuspendRadioButtons.put(SuspendPolicy.NONE, mySuspendNoneRadioButton);
    @NonNls String card = type.isSuspendThreadSupported() ? "radioButtons" : "checkbox";
    ((CardLayout)mySuspendPolicyPanel.getLayout()).show(mySuspendPolicyPanel, card);

    myMasterBreakpointComboBox = new ComboBox(300);
    myMasterBreakpointComboBoxPanel.add(myMasterBreakpointComboBox, BorderLayout.CENTER);
    myMasterBreakpointComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateAfterBreakpointHitPanel();
      }
    });
    myMasterBreakpointComboBox.setRenderer(new BreakpointsListCellRenderer<B>());
    fillMasterBreakpointComboBox();

    XDebuggerEditorsProvider debuggerEditorsProvider = type.getEditorsProvider();
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

      myConditionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, "breakpointCondition", myBreakpoint.getSourcePosition());
      JComponent conditionComponent = myConditionComboBox.getComponent();
      myConditionExpressionPanel.add(conditionComponent, BorderLayout.CENTER);
      myConditionComboBox.setEnabled(false);
      myConditionCheckBox.addActionListener(listener);
      DebuggerUIUtil.focusEditorOnCheck(myConditionCheckBox, conditionComponent);
    }
    else {
      myLogExpressionCheckBox.setVisible(false);
      myConditionCheckBox.setVisible(false);
    }

    myCustomPanels = new ArrayList<XBreakpointCustomPropertiesPanel<B>>();
    XBreakpointCustomPropertiesPanel<B> customConditionPanel = type.createCustomConditionsPanel();
    if (customConditionPanel != null) {
      myCustomConditionsPanelWrapper.add(customConditionPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customConditionPanel);
    }

    if (debuggerEditorsProvider == null && customConditionPanel == null) {
      myConditionsPanel.setVisible(false);
    }

    XBreakpointCustomPropertiesPanel<B> customPropertiesPanel = type.createCustomPropertiesPanel();
    if (customPropertiesPanel != null) {
      myCustomPropertiesPanelWrapper.add(customPropertiesPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customPropertiesPanel);
    }

    loadProperties();
  }

  private void updateAfterBreakpointHitPanel() {
    boolean enable = myMasterBreakpointComboBox.getSelectedItem() != null;
    GuiUtils.enableChildren(enable, myAfterBreakpointHitPanel);
  }

  private void onCheckboxChanged() {
    if (myLogExpressionComboBox != null) {
      myLogExpressionComboBox.setEnabled(myLogExpressionCheckBox.isSelected());
    }
    if (myConditionComboBox != null) {
      myConditionComboBox.setEnabled(myConditionCheckBox.isSelected());
    }
  }

  private void loadProperties() {
    SuspendPolicy suspendPolicy = myBreakpoint.getSuspendPolicy();
    mySuspendRadioButtons.get(suspendPolicy).setSelected(true);
    mySuspendCheckBox.setSelected(suspendPolicy != SuspendPolicy.NONE);

    myLogMessageCheckBox.setSelected(myBreakpoint.isLogMessage());
    if (myLogExpressionComboBox != null) {
      String logExpression = myBreakpoint.getLogExpression();
      myLogExpressionCheckBox.setSelected(logExpression != null);
      myLogExpressionComboBox.setText(logExpression != null ? logExpression : "");
    }
    if (myConditionComboBox != null) {
      String condition = myBreakpoint.getCondition();
      myConditionCheckBox.setSelected(condition != null);
      myConditionComboBox.setText(condition != null ? condition : "");
    }

    XBreakpoint<?> masterBreakpoint = myDependentBreakpointManager.getMasterBreakpoint(myBreakpoint);
    if (masterBreakpoint != null) {
      myMasterBreakpointComboBox.setSelectedItem(masterBreakpoint);
      myLeaveEnabledRadioButton.setSelected(myDependentBreakpointManager.isLeaveEnabled(myBreakpoint));
    }
    updateAfterBreakpointHitPanel();

    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.loadFrom(myBreakpoint);
    }

    onCheckboxChanged();
  }

  private void fillMasterBreakpointComboBox() {
    myMasterBreakpointComboBox.removeAllItems();
    myMasterBreakpointComboBox.addItem(null);
    for (B breakpoint : myBreakpointManager.getBreakpoints(XBreakpointUtil.getType(myBreakpoint))) {
      if (breakpoint != myBreakpoint) {
        myMasterBreakpointComboBox.addItem(breakpoint);
      }
    }
  }

  public B getBreakpoint() {
    return myBreakpoint;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void saveProperties() {
    myBreakpoint.setSuspendPolicy(getConfiguredSuspendPolicy());
    myBreakpoint.setLogMessage(myLogMessageCheckBox.isSelected());

    if (myLogExpressionComboBox != null) {
      String logExpression = myLogExpressionCheckBox.isSelected() ? myLogExpressionComboBox.getText() : null;
      myBreakpoint.setLogExpression(logExpression);
      myLogExpressionComboBox.saveTextInHistory();
    }

    if (myConditionComboBox != null) {
      String condition = myConditionCheckBox.isSelected() ? myConditionComboBox.getText() : null;
      myBreakpoint.setCondition(condition);
      myConditionComboBox.saveTextInHistory();
    }

    XBreakpoint<?> masterBreakpoint = (XBreakpoint<?>)myMasterBreakpointComboBox.getSelectedItem();
    if (masterBreakpoint == null) {
      myDependentBreakpointManager.clearMasterBreakpoint(myBreakpoint);
    }
    else {
      myDependentBreakpointManager.setMasterBreakpoint(myBreakpoint, masterBreakpoint, myLeaveEnabledRadioButton.isSelected());
    }

    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.saveTo(myBreakpoint);
    }
  }

  private SuspendPolicy getConfiguredSuspendPolicy() {
    if (!myBreakpoint.getType().isSuspendThreadSupported()) {
      return mySuspendCheckBox.isSelected() ? SuspendPolicy.ALL : SuspendPolicy.NONE;
    }

    for (SuspendPolicy policy : mySuspendRadioButtons.keySet()) {
      if (mySuspendRadioButtons.get(policy).isSelected()) {
        return policy;
      }
    }
    return SuspendPolicy.ALL;
  }

  public void dispose() {
    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.dispose();
    }
  }

  private static class BreakpointsListCellRenderer<B extends XBreakpoint<?>> extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(final JList list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {
      Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value != null) {
        B breakpoint = (B)value;
        setText(XBreakpointUtil.getDisplayText(breakpoint));
        setIcon(breakpoint.getType().getEnabledIcon());
      }
      else {
        setText(XDebuggerBundle.message("xbreakpoint.master.breakpoint.none"));
        setIcon(null);
      }
      return component;
    }
  }
}
