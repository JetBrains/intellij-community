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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;

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
  private JPanel myCustomRightPropertiesPanelWrapper;
  private final List<XBreakpointCustomPropertiesPanel<B>> myCustomPanels;

  private List<XBreakpointPropertiesSubPanel<B>> mySubPanels = new ArrayList<XBreakpointPropertiesSubPanel<B>>();

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

    myCustomPanels = new ArrayList<XBreakpointCustomPropertiesPanel<B>>();
    if (debuggerEditorsProvider != null) {
      final XBreakpointCustomPropertiesPanel<B> conditionPanel;
      if (debuggerEditorsProvider instanceof XDebuggerComboBoxProvider) {
        conditionPanel = ((XDebuggerComboBoxProvider<B>)debuggerEditorsProvider).createConditionComboBoxPanel(
            project, debuggerEditorsProvider, DefaultConditionComboBoxPanel.HISTORY_KEY, myBreakpoint.getSourcePosition());
      }
      else {
        conditionPanel =
          new DefaultConditionComboBoxPanel<B>(project, debuggerEditorsProvider, myBreakpoint.getSourcePosition());
      }
      myConditionExpressionPanel.add(conditionPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(conditionPanel);
      myMainPanel.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent event) {
          IdeFocusManager.findInstance().requestFocus(conditionPanel.getComponent(), false);
        }
      });
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

    for (XBreakpointCustomPropertiesPanel<B> customPanel : myCustomPanels) {
      customPanel.saveTo(myBreakpoint);
    }
    myBreakpoint.setEnabled(myEnabledCheckbox.isSelected());
  }

  public void loadProperties() {
    for (XBreakpointPropertiesSubPanel<B> panel : mySubPanels) {
      panel.loadProperties();
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
