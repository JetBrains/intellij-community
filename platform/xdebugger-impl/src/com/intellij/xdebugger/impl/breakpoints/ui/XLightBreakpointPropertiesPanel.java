// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class XLightBreakpointPropertiesPanel implements XSuspendPolicyPanel.Delegate {
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

  private void createUIComponents() {
    myRestoreLink = new ActionLink(XDebuggerBundle.message("xbreakpoints.restore.label"), e -> {
      WriteAction.run(() -> ((XBreakpointManagerImpl)XDebuggerManager.getInstance(myBreakpoint.getProject()).getBreakpointManager()).restoreLastRemovedBreakpoint());
    });
  }

  public interface Delegate {
    void showMoreOptions();
  }

  private JPanel myConditionPanel;
  private JPanel myMainPanel;

  public Delegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }

  private Delegate myDelegate;

  private XSuspendPolicyPanel mySuspendPolicyPanel;
  private XBreakpointActionsPanel myActionsPanel;
  private XMasterBreakpointPanel myMasterBreakpointPanel;
  private JPanel myCustomPropertiesPanelWrapper;
  private JPanel myCustomConditionsPanelWrapper;
  private JCheckBox myEnabledCheckbox;
  private JPanel myCustomRightPropertiesPanelWrapper;
  private JBCheckBox myConditionEnabledCheckbox;
  private JPanel myCustomTopPropertiesPanelWrapper;
  private JBLabel myBreakpointNameLabel;
  private JPanel myConditionCheckboxPanel;
  private JPanel myLanguageChooserPanel;
  private JPanel myConditionExpressionPanel;
  private ActionLink myRestoreLink;
  private final List<XBreakpointCustomPropertiesPanel> myCustomPanels;

  private final List<XBreakpointPropertiesSubPanel> mySubPanels = new ArrayList<>();

  private @Nullable XDebuggerExpressionComboBox myConditionComboBox;

  private final XBreakpointBase myBreakpoint;

  private final boolean myShowAllOptions;
  private final boolean myIsEditorBalloon;

  public void setDetailView(DetailView detailView) {
    myMasterBreakpointPanel.setDetailView(detailView);
  }

  /**
   * @deprecated use {@link XLightBreakpointPropertiesPanel#XLightBreakpointPropertiesPanel(Project, XBreakpointManager, XBreakpointBase, boolean, boolean)}
   */
  @Deprecated(forRemoval = true)
  public XLightBreakpointPropertiesPanel(Project project, XBreakpointManager breakpointManager, XBreakpointBase breakpoint, boolean showAllOptions) {
    this(project, breakpointManager, breakpoint, showAllOptions, false);
  }

  public XLightBreakpointPropertiesPanel(Project project, XBreakpointManager breakpointManager, XBreakpointBase breakpoint,
                                         boolean showAllOptions, boolean isEditorBalloon) {
    myBreakpoint = breakpoint;
    myShowAllOptions = showAllOptions;
    myIsEditorBalloon = isEditorBalloon;
    XBreakpointType breakpointType = breakpoint.getType();

    if (breakpointType.getVisibleStandardPanels().contains(XBreakpointType.StandardPanels.SUSPEND_POLICY)) {
      mySuspendPolicyPanel.init(project, breakpointManager, breakpoint);
      mySuspendPolicyPanel.setDelegate(this);
      mySubPanels.add(mySuspendPolicyPanel);
    }
    else {
      mySuspendPolicyPanel.hide();
    }

    if (breakpointType.getVisibleStandardPanels().contains(XBreakpointType.StandardPanels.DEPENDENCY)) {
      myMasterBreakpointPanel.init(project, breakpointManager, breakpoint);
      mySubPanels.add(myMasterBreakpointPanel);
    }
    else {
      myMasterBreakpointPanel.hide();
    }

    XDebuggerEditorsProvider debuggerEditorsProvider = breakpointType.getEditorsProvider(breakpoint, project);

    if (breakpointType.getVisibleStandardPanels().contains(XBreakpointType.StandardPanels.ACTIONS)) {
      myActionsPanel.init(project, breakpointManager, breakpoint, debuggerEditorsProvider);
      mySubPanels.add(myActionsPanel);
    }
    else {
      myActionsPanel.hide();
    }

    myCustomPanels = new ArrayList<>();
    if (debuggerEditorsProvider != null) {
      myConditionEnabledCheckbox = new JBCheckBox(XDebuggerBundle.message("xbreakpoints.condition.checkbox"));
      myConditionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, CONDITION_HISTORY_ID, null, true, false);
      myLanguageChooserPanel.add(myConditionComboBox.getLanguageChooser(), BorderLayout.CENTER);
      myConditionExpressionPanel.add(myConditionComboBox.getComponent(), BorderLayout.CENTER);
      myConditionEnabledCheckbox.addActionListener(e -> onCheckboxChanged());
      DebuggerUIUtil.focusEditorOnCheck(myConditionEnabledCheckbox, myConditionComboBox.getEditorComponent());
    } else {
      myConditionPanel.setVisible(false);
    }

    myShowMoreOptions = false;
    for (XBreakpointPropertiesSubPanel panel : mySubPanels) {
      if (panel.lightVariant(showAllOptions)) {
        myShowMoreOptions = true;
      }
    }

    XBreakpointCustomPropertiesPanel customPropertiesPanel = breakpointType.createCustomPropertiesPanel(project);
    if (customPropertiesPanel != null) {
      myCustomPropertiesPanelWrapper.add(customPropertiesPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customPropertiesPanel);
    }
    else {
      myCustomPropertiesPanelWrapper.getParent().remove(myCustomPropertiesPanelWrapper);
    }

    XBreakpointCustomPropertiesPanel customConditionPanel = breakpointType.createCustomConditionsPanel();
    if (customConditionPanel != null) {
      myCustomConditionsPanelWrapper.add(customConditionPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customConditionPanel);
    }
    else {
      myCustomConditionsPanelWrapper.getParent().remove(myCustomConditionsPanelWrapper);
    }

    XBreakpointCustomPropertiesPanel customRightConditionPanel = breakpointType.createCustomRightPropertiesPanel(project);
    if (customRightConditionPanel != null && (showAllOptions || customRightConditionPanel.isVisibleOnPopup(breakpoint))) {
      myCustomRightPropertiesPanelWrapper.add(customRightConditionPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customRightConditionPanel);
    }
    else {
      // see IDEA-125745
      myCustomRightPropertiesPanelWrapper.getParent().remove(myCustomRightPropertiesPanelWrapper);
    }

    XBreakpointCustomPropertiesPanel customTopPropertiesPanel = breakpointType.createCustomTopPropertiesPanel(project);
    if (customTopPropertiesPanel != null) {
      myCustomTopPropertiesPanelWrapper.add(customTopPropertiesPanel.getComponent(), BorderLayout.CENTER);
      myCustomPanels.add(customTopPropertiesPanel);
    }
    else {
      myCustomTopPropertiesPanelWrapper.getParent().remove(myCustomTopPropertiesPanelWrapper);
    }

    myMainPanel.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent event) {
        JComponent compToFocus = null;
        if (myConditionComboBox != null && myConditionComboBox.getComboBox().isEnabled()) {
          compToFocus = myConditionComboBox.getEditorComponent();
        }
        else if (breakpointType.getVisibleStandardPanels().contains(XBreakpointType.StandardPanels.ACTIONS)) {
          compToFocus = myActionsPanel.getDefaultFocusComponent();
        }
        if (compToFocus != null) {
          IdeFocusManager.findInstance().requestFocus(compToFocus, false);
        }
      }
    });

    myEnabledCheckbox.addActionListener(e -> myBreakpoint.setEnabled(myEnabledCheckbox.isSelected()));

    myRestoreLink.setVisible(false);
    ReadAction.nonBlocking(() -> {
        XSourcePosition sourcePosition = myBreakpoint.getSourcePosition();
        XBreakpointBase lastRemovedBreakpoint = ((XBreakpointManagerImpl)breakpointManager).getLastRemovedBreakpoint();
        boolean restore = lastRemovedBreakpoint != null &&
                    breakpointType.equals(lastRemovedBreakpoint.getType()) &&
                    XSourcePosition.isOnTheSameLine(sourcePosition, lastRemovedBreakpoint.getSourcePosition()) &&
                    XBreakpointManagerImpl.statesAreDifferent(lastRemovedBreakpoint.getState(), breakpoint.getState(), true);
        return Pair.create(sourcePosition, restore);
      })
      .finishOnUiThread(ModalityState.defaultModalityState(), pair -> {
        XSourcePosition sourcePosition = pair.getFirst();
        if (myConditionComboBox != null) {
          myConditionComboBox.setSourcePosition(sourcePosition);
        }
        myActionsPanel.setSourcePosition(sourcePosition);
        myRestoreLink.setVisible(pair.getSecond());
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private void onCheckboxChanged() {
    if (myConditionComboBox != null) {
      myConditionComboBox.setEnabled(myConditionEnabledCheckbox.isSelected());
    }
  }

  public void saveProperties() {
    mySubPanels.forEach(XBreakpointPropertiesSubPanel::saveProperties);

    if (myConditionComboBox != null) {
      XExpression expression = myConditionComboBox.getExpression();
      XExpression condition = !XDebuggerUtilImpl.isEmptyExpression(expression) ? expression : null;
      myBreakpoint.setConditionEnabled(condition == null || myConditionEnabledCheckbox.isSelected());
      myBreakpoint.setConditionExpression(condition);
      myConditionComboBox.saveTextInHistory();
    }

    for (XBreakpointCustomPropertiesPanel customPanel : myCustomPanels) {
      customPanel.saveTo(myBreakpoint);
    }
    myBreakpoint.setEnabled(myEnabledCheckbox.isSelected());
  }

  public void loadProperties() {
    mySubPanels.forEach(XBreakpointPropertiesSubPanel::loadProperties);

    if (myConditionComboBox != null) {
      XExpression condition = myBreakpoint.getConditionExpressionInt();
      myConditionComboBox.setExpression(condition);
      boolean hideCheckbox = !myShowAllOptions && condition == null;
      myConditionEnabledCheckbox.setSelected(hideCheckbox || (myBreakpoint.isConditionEnabled() && condition != null));
      myConditionCheckboxPanel.removeAll();
      if (hideCheckbox) {
        JBLabel label = new JBLabel(XDebuggerBundle.message("xbreakpoints.condition.checkbox"));
        label.setBorder(JBUI.Borders.empty(0, 4, 4, 0));
        label.setLabelFor(myConditionComboBox.getComboBox());
        myConditionCheckboxPanel.add(label);
        myConditionExpressionPanel.setBorder(JBUI.Borders.emptyLeft(3));
      }
      else {
        myConditionCheckboxPanel.add(myConditionEnabledCheckbox);
        myConditionExpressionPanel.setBorder(JBUI.Borders.emptyLeft(UIUtil.getCheckBoxTextHorizontalOffset(myConditionEnabledCheckbox)));
      }

      onCheckboxChanged();
    }
    
    for (XBreakpointCustomPropertiesPanel customPanel : myCustomPanels) {
      customPanel.loadFrom(myBreakpoint);
    }
    myEnabledCheckbox.setSelected(myBreakpoint.isEnabled());
    myBreakpointNameLabel.setText(getBreakpointNameLabel());
  }

  @Nls
  private String getBreakpointNameLabel() {
    var description = XBreakpointUtil.getGeneralDescription(myBreakpoint);
    if (myIsEditorBalloon) {
      // Use tooltip-like description in this case.
      return description;
    }

    var itemTitleText = XBreakpointUtil.getShortText(myBreakpoint);
    if (description.equals(itemTitleText) || itemTitleText.contains(description)) {
      return itemTitleText;
    }
    if (description.contains(itemTitleText)) {
      return description;
    }

    // Try to take both of them for a better result.
    return XDebuggerBundle.message("xbreakpoints.dialog.double.breakpoint.title", itemTitleText, description);
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void dispose() {
    myActionsPanel.dispose();
    myCustomPanels.forEach(Disposer::dispose);
    myCustomPanels.clear();
  }
}
