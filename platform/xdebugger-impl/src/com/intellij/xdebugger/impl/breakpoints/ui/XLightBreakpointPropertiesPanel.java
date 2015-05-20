/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

public class XLightBreakpointPropertiesPanel<B extends XBreakpointBase<?,?,?>> implements XSuspendPolicyPanel.Delegate {
  public static final String CONDITION_HISTORY_ID = "breakpointCondition";

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
  private JPanel myCustomRightPropertiesPanelWrapper;
  private JBCheckBox myConditionEnabledCheckbox;
  private JPanel myCustomTopPropertiesPanelWrapper;
  private JPanel myConditionEnabledPanel;
  private final List<XBreakpointCustomPropertiesPanel<B>> myCustomPanels;

  private final List<XBreakpointPropertiesSubPanel<B>> mySubPanels = new ArrayList<XBreakpointPropertiesSubPanel<B>>();

  private XDebuggerExpressionComboBox myConditionComboBox;

  private final B myBreakpoint;

  private final boolean myShowAllOptions;
  private static final String CONDITION_ENABLED_LABEL = "label";
  private static final String CONDITION_ENABLED_CHECKBOX = "checkbox";

  public void setDetailView(DetailView detailView) {
    myMasterBreakpointPanel.setDetailView(detailView);
  }

  public XLightBreakpointPropertiesPanel(Project project, XBreakpointManager breakpointManager, B breakpoint, boolean showAllOptions) {
    myBreakpoint = breakpoint;
    myShowAllOptions = showAllOptions;
    XBreakpointType<B, ?> breakpointType = XBreakpointUtil.getType(breakpoint);

    mySuspendPolicyPanel.init(project, breakpointManager, breakpoint);
    mySuspendPolicyPanel.setDelegate(this);

    mySubPanels.add(mySuspendPolicyPanel);
    myMasterBreakpointPanel.init(project, breakpointManager, breakpoint);
    mySubPanels.add(myMasterBreakpointPanel);
    XDebuggerEditorsProvider debuggerEditorsProvider = breakpointType.getEditorsProvider(breakpoint, project);

    myActionsPanel.init(project, breakpointManager, breakpoint, debuggerEditorsProvider);
    mySubPanels.add(myActionsPanel);

    myCustomPanels = new ArrayList<XBreakpointCustomPropertiesPanel<B>>();
    if (debuggerEditorsProvider != null) {
      myConditionEnabledCheckbox = new JBCheckBox(XDebuggerBundle.message("xbreakpoints.condition.checkbox"));
      JBLabel conditionEnabledLabel = new JBLabel(XDebuggerBundle.message("xbreakpoints.condition.checkbox"));
      conditionEnabledLabel.setBorder(UIUtil.getTextAlignBorder(myConditionEnabledCheckbox));
      myConditionEnabledPanel.add(myConditionEnabledCheckbox, CONDITION_ENABLED_CHECKBOX);
      myConditionEnabledPanel.add(conditionEnabledLabel, CONDITION_ENABLED_LABEL);
      myConditionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, CONDITION_HISTORY_ID, myBreakpoint.getSourcePosition());
      JComponent conditionComponent = myConditionComboBox.getComponent();
      myConditionExpressionPanel.add(conditionComponent, BorderLayout.CENTER);
      myConditionEnabledCheckbox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          onCheckboxChanged();
        }
      });
      DebuggerUIUtil.focusEditorOnCheck(myConditionEnabledCheckbox, myConditionComboBox.getEditorComponent());
    } else {
      myConditionPanel.setVisible(false);
    }

    myShowMoreOptions = false;
    for (XBreakpointPropertiesSubPanel<B> panel : mySubPanels) {
      if (panel.lightVariant(showAllOptions)) {
        myShowMoreOptions = true;
      }
    }

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

    XBreakpointCustomPropertiesPanel<B> customRightConditionPanel = breakpointType.createCustomRightPropertiesPanel(project);
    if (customRightConditionPanel != null && (showAllOptions || customRightConditionPanel.isVisibleOnPopup(breakpoint))) {
      myCustomRightPropertiesPanelWrapper.add(customRightConditionPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customRightConditionPanel);
    }
    else {
      // see IDEA-125745
      myCustomRightPropertiesPanelWrapper.getParent().remove(myCustomRightPropertiesPanelWrapper);
    }

    XBreakpointCustomPropertiesPanel<B> customTopPropertiesPanel = breakpointType.createCustomTopPropertiesPanel(project);
    if (customTopPropertiesPanel != null) {
      myCustomTopPropertiesPanelWrapper.add(customTopPropertiesPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customTopPropertiesPanel);
    }

    myMainPanel.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent event) {
        JComponent compToFocus;
        if (myConditionComboBox != null && myConditionComboBox.getComboBox().isEnabled()) {
          compToFocus = myConditionComboBox.getEditorComponent();
        }
        else {
          compToFocus = myActionsPanel.getDefaultFocusComponent();
        }
        if (compToFocus != null) {
          IdeFocusManager.findInstance().requestFocus(compToFocus, false);
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

  private void onCheckboxChanged() {
    if (myConditionComboBox != null) {
      myConditionComboBox.setEnabled(myConditionEnabledCheckbox.isSelected());
    }
  }

  public void saveProperties() {
    for (XBreakpointPropertiesSubPanel<B> panel : mySubPanels) {
      panel.saveProperties();
    }

    if (myConditionComboBox != null) {
      XExpression expression = myConditionComboBox.getExpression();
      XExpression condition = !XDebuggerUtilImpl.isEmptyExpression(expression) ? expression : null;
      myBreakpoint.setConditionEnabled(condition == null || myConditionEnabledCheckbox.isSelected());
      myBreakpoint.setConditionExpression(condition);
      myConditionComboBox.saveTextInHistory();
    }

    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.saveTo(myBreakpoint);
    }
    myBreakpoint.setEnabled(myEnabledCheckbox.isSelected());
  }

  public void loadProperties() {
    for (XBreakpointPropertiesSubPanel<B> panel : mySubPanels) {
      panel.loadProperties();
    }

    if (myConditionComboBox != null) {
      XExpression condition = myBreakpoint.getConditionExpressionInt();
      myConditionComboBox.setExpression(condition);
      boolean hideCheckbox = !myShowAllOptions && condition == null;
      myConditionEnabledCheckbox.setSelected(hideCheckbox || (myBreakpoint.isConditionEnabled() && condition != null));
      ((CardLayout)myConditionEnabledPanel.getLayout()).show(myConditionEnabledPanel, hideCheckbox ? CONDITION_ENABLED_LABEL : CONDITION_ENABLED_CHECKBOX);

      onCheckboxChanged();
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

  public void dispose() {
    myActionsPanel.dispose();
    for (XBreakpointCustomPropertiesPanel<B> panel : myCustomPanels) {
      panel.dispose();
    }
  }
}
