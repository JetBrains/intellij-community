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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

public class XLightBreakpointPropertiesPanel<B extends XBreakpoint<?>> implements XSuspendPolicyPanel.Delegate {
  @SuppressWarnings("UnusedDeclaration")
  public boolean showMoreOptions() {
    return myShowMoreOptions;
  }

  private boolean myShowMoreOptions;

  @Override
  public void showMoreOptionsIfNeeded() {
    if (myShowMoreOptions) {
      if (myDelegate != null) {
        myDelegate.showMoreOptions();
      }
    }
  }

  public interface Delegate {
    void showMoreOptions();
  }

  private JPanel myConditionExpressionPanel;
  private JPanel myConditionPanel;
  private JPanel myMainPanel;

  public Delegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }

  private Delegate myDelegate;

  private XSuspendPolicyPanel<B> mySuspendPolicyPanel;
  private XBreakpointActionsPanel<B> myActionsPanel;
  private XMasterBreakpointPanel<B> myMasterBreakpointPanel;
  private JPanel myCustomPropertiesPanelWrapper;
  private JPanel myCustomConditionsPanelWrapper;
  private JCheckBox myEnabledCheckbox;
  private final List<XBreakpointCustomPropertiesPanel<B>> myCustomPanels;

  private List<XBreakpointPropertiesSubPanel<B>> mySubPanels = new ArrayList<XBreakpointPropertiesSubPanel<B>>();

  private XDebuggerExpressionComboBox myConditionComboBox;

  private B myBreakpoint;

  public void setDetailView(DetailView detailView) {
    myMasterBreakpointPanel.setDetailView(detailView);
  }

  public XLightBreakpointPropertiesPanel(Project project, XBreakpointManager breakpointManager, B breakpoint, boolean showAllOptions) {
    myBreakpoint = breakpoint;
    XBreakpointType<B, ?> breakpointType = XBreakpointUtil.getType(breakpoint);

    mySuspendPolicyPanel.init(project, breakpointManager, breakpoint);
    mySuspendPolicyPanel.setDelegate(this);

    mySubPanels.add(mySuspendPolicyPanel);
    myMasterBreakpointPanel.init(project, breakpointManager, breakpoint);
    mySubPanels.add(myMasterBreakpointPanel);
    XDebuggerEditorsProvider debuggerEditorsProvider = breakpointType.getEditorsProvider(breakpoint, project);

    myActionsPanel.init(project, breakpointManager, breakpoint, debuggerEditorsProvider);
    mySubPanels.add(myActionsPanel);

    if (debuggerEditorsProvider != null) {
      myConditionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, "breakpointCondition", myBreakpoint.getSourcePosition());
      JComponent conditionComponent = myConditionComboBox.getComponent();
      myConditionExpressionPanel.add(conditionComponent, BorderLayout.CENTER);
    } else {
      myConditionPanel.setVisible(false);
    }

    myShowMoreOptions = false;
    for (XBreakpointPropertiesSubPanel<B> panel : mySubPanels) {
      if (panel.lightVariant(showAllOptions)) {
        myShowMoreOptions = true;
      }
    }

    myCustomPanels = new ArrayList<XBreakpointCustomPropertiesPanel<B>>();
    XBreakpointCustomPropertiesPanel<B> customPropertiesPanel = breakpointType.createCustomPropertiesPanel();
    if (customPropertiesPanel != null) {
      myCustomPropertiesPanelWrapper.add(customPropertiesPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customPropertiesPanel);
    }

    XBreakpointCustomPropertiesPanel<B> customConditionPanel = breakpointType.createCustomConditionsPanel();
    if (customConditionPanel != null) {
      myCustomConditionsPanelWrapper.add(customConditionPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customConditionPanel);
    }

    myMainPanel.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent event) {
        if (myConditionComboBox != null) {
          IdeFocusManager.findInstance().requestFocus(myConditionComboBox.getComponent(), false);
        }
      }
    });
    myEnabledCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        myBreakpoint.setEnabled(myEnabledCheckbox.isSelected());
      }
    });
  }

  public void saveProperties() {
    for (XBreakpointPropertiesSubPanel<B> panel : mySubPanels) {
      panel.saveProperties();
    }

    if (myConditionComboBox != null) {
      final String condition = StringUtil.nullize(myConditionComboBox.getText(), true);
      myBreakpoint.setCondition(condition);
      if (condition != null) {
        myConditionComboBox.saveTextInHistory();
      }
    }

    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.saveTo(myBreakpoint);
    }
    if (!myCustomPanels.isEmpty()) {
      ((XBreakpointBase)myBreakpoint).fireBreakpointChanged();
    }
    myBreakpoint.setEnabled(myEnabledCheckbox.isSelected());
  }

  public void loadProperties() {
    for (XBreakpointPropertiesSubPanel<B> panel : mySubPanels) {
      panel.loadProperties();
    }
    
    if (myConditionComboBox != null) {
      myConditionComboBox.setText(StringUtil.notNullize(myBreakpoint.getCondition()));
    }

    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.loadFrom(myBreakpoint);
    }
    myEnabledCheckbox.setSelected(myBreakpoint.isEnabled());
    myEnabledCheckbox.setText(XBreakpointUtil.getShortText(myBreakpoint) + " enabled");
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }
}
