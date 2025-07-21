// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
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
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.*;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.xdebugger.impl.breakpoints.XBreakpointProxyKt.asProxy;

@ApiStatus.Internal
public class XLightBreakpointPropertiesPanel implements XSuspendPolicyPanel.Delegate {
  public static final String CONDITION_HISTORY_ID = "breakpointCondition";

  @SuppressWarnings("UnusedDeclaration")
  public boolean showMoreOptions() {
    return myShowMoreActionOptionsIsAvailable;
  }

  private boolean myShowMoreActionOptionsIsAvailable;

  @Override
  public void showActionOptionsIfNeeded() {
    if (myShowMoreActionOptionsIsAvailable) {
      if (myDelegate != null) {
        myDelegate.showActionOptions();
      }
    }
  }

  private void createUIComponents() {
    myRestoreLink = new ActionLink(XDebuggerBundle.message("xbreakpoints.restore.label"), e -> {
      myBreakpointManager.restoreRemovedBreakpoint(myBreakpoint);
      if (myBalloon != null) {
        myBalloon.hide();
      }
    });
  }

  public interface Delegate {
    void showActionOptions();
  }

  private JPanel myConditionPanel;
  private JPanel myMainPanel;

  public Delegate getDelegate() {
    return myDelegate;
  }

  public void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }

  @ApiStatus.Internal
  public void setBalloon(@NotNull Balloon balloon) {
    myBalloon = balloon;
  }

  private Delegate myDelegate;
  private @Nullable Balloon myBalloon;

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

  private final XBreakpointProxy myBreakpoint;

  private final boolean myShowAllOptions;
  private final boolean myIsEditorBalloon;

  public void setDetailView(DetailView detailView) {
    myMasterBreakpointPanel.setDetailView(detailView);
  }

  public XLightBreakpointPropertiesPanel(Project project, XBreakpointManagerProxy breakpointManager, XBreakpointProxy breakpoint,
                                         boolean showAllOptions, boolean isEditorBalloon) {
    this(project, breakpointManager, breakpoint, showAllOptions, showAllOptions, isEditorBalloon);
  }

  public XLightBreakpointPropertiesPanel(Project project, XBreakpointManager breakpointManager, XBreakpointBase breakpoint,
                                         boolean showActionOptions, boolean showAllOptions, boolean isEditorBalloon) {
    this(project, new XBreakpointManagerProxy.Monolith((XBreakpointManagerImpl)breakpointManager),
         asProxy(breakpoint), showActionOptions, showAllOptions, isEditorBalloon);
  }

  private final @NotNull XBreakpointManagerProxy myBreakpointManager;

  public XLightBreakpointPropertiesPanel(Project project, XBreakpointManagerProxy breakpointManager, XBreakpointProxy breakpoint,
                                         boolean showActionOptions, boolean showAllOptions, boolean isEditorBalloon) {
    myBreakpointManager = breakpointManager;
    myBreakpoint = breakpoint;
    myShowAllOptions = showAllOptions;
    myIsEditorBalloon = isEditorBalloon;
    XBreakpointTypeProxy breakpointType = breakpoint.getType();

    if (breakpointType.getVisibleStandardPanels().contains(XBreakpointType.StandardPanels.SUSPEND_POLICY)) {
      mySuspendPolicyPanel.init(project, breakpoint);
      mySuspendPolicyPanel.setDelegate(this);
      mySubPanels.add(mySuspendPolicyPanel);
    }
    else {
      mySuspendPolicyPanel.hide();
    }

    if (breakpointType.getVisibleStandardPanels().contains(XBreakpointType.StandardPanels.DEPENDENCY)) {
      myMasterBreakpointPanel.init(project, breakpoint);
      mySubPanels.add(myMasterBreakpointPanel);
    }
    else {
      myMasterBreakpointPanel.hide();
    }

    XDebuggerEditorsProvider debuggerEditorsProvider = breakpoint.getEditorsProvider();

    if (breakpointType.getVisibleStandardPanels().contains(XBreakpointType.StandardPanels.ACTIONS)) {
      myActionsPanel.init(project, breakpoint, debuggerEditorsProvider, myShowAllOptions);
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

    myShowMoreActionOptionsIsAvailable = false;
    for (XBreakpointPropertiesSubPanel panel : mySubPanels) {
      if (panel instanceof XBreakpointActionsPanel) {
        if (panel.lightVariant(showActionOptions || myShowAllOptions)) {
          myShowMoreActionOptionsIsAvailable = true;
        }
      }
      else {
        panel.lightVariant(myShowAllOptions);
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
    boolean isVisibleOnPopup = false;
    if (customRightConditionPanel != null && myBreakpoint instanceof XBreakpointProxy.Monolith) {
      //noinspection unchecked
      isVisibleOnPopup = customRightConditionPanel.isVisibleOnPopup(((XBreakpointProxy.Monolith)breakpoint).getBreakpoint());
    }
    if (customRightConditionPanel != null && (myShowAllOptions || isVisibleOnPopup)) {
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
        var actionsAvailable = breakpointType.getVisibleStandardPanels().contains(XBreakpointType.StandardPanels.ACTIONS);
        var conditionEditable = myConditionComboBox != null && myConditionComboBox.getComboBox().isEnabled();
        var isSuspending = breakpoint.getSuspendPolicy() != SuspendPolicy.NONE;
        if (actionsAvailable && (!isSuspending || !conditionEditable)) {
          // Focus actions panel in case of non-suspending breakpoint (or if condition is explicitly disabled).
          // This is important for the "Add Logging Breakpoint" action which should focus on the logging expression.
          compToFocus = myActionsPanel.getDefaultFocusComponent();
        }
        else if (conditionEditable) {
          compToFocus = myConditionComboBox.getEditorComponent();
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
        XBreakpointProxy lastRemovedBreakpoint = breakpointManager.getLastRemovedBreakpoint();
        boolean restore = lastRemovedBreakpoint != null &&
                    breakpointType.equals(lastRemovedBreakpoint.getType()) &&
                    XSourcePosition.isOnTheSameLine(sourcePosition, lastRemovedBreakpoint.getSourcePosition()) &&
                          !lastRemovedBreakpoint.haveSameState(breakpoint, true);
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

    if (myBreakpoint instanceof XBreakpointProxy.Monolith) {
      for (XBreakpointCustomPropertiesPanel customPanel : myCustomPanels) {
        customPanel.saveTo(((XBreakpointProxy.Monolith)myBreakpoint).getBreakpoint());
      }
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

    if (myBreakpoint instanceof XBreakpointProxy.Monolith) {
      for (XBreakpointCustomPropertiesPanel customPanel : myCustomPanels) {
        customPanel.loadFrom(((XBreakpointProxy.Monolith)myBreakpoint).getBreakpoint());
      }
    }
    myEnabledCheckbox.setSelected(myBreakpoint.isEnabled());
    myBreakpointNameLabel.setText(getBreakpointNameLabel());
  }

  private @Nls String getBreakpointNameLabel() {
    var description = myBreakpoint.getGeneralDescription();
    if (myIsEditorBalloon) {
      // Use tooltip-like description in this case.
      return description;
    }

    var itemTitleText = myBreakpoint.getShortText();

    // By default, historically, XLineBreakpointType implements above methods as "path/name:line" and "name:line"
    // (XLineBreakpointType.getDisplayText & XLineBreakpointType.getShortText).
    // We don't want to output both of these texts to prevent duplication.
    // 1. In the trivial case, we just can check if one string is substring of another:
    if (description.contains(itemTitleText)) {
      return description;
    }
    if (itemTitleText.contains(description)) {
      return itemTitleText;
    }
    // 2. However, in case of a long path or name, these strings can be shortened and won't be substring of each other (see IJPL-172094).
    // So we explicitly check if the description is equal to the default one and disregard the short text (it's likely to be useless).
    if (isDefaultDescriptionOfXLineBreakpoint(description)) {
      return description;
    }

    // Otherwise, we take both of them for a better result.
    // E.g., in case of Java it might be: "Hello.java:42, Line breakpoint".
    return XDebuggerBundle.message("xbreakpoints.dialog.double.breakpoint.title", itemTitleText, description);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private boolean isDefaultDescriptionOfXLineBreakpoint(String description) {
    if (myBreakpoint instanceof XLineBreakpoint<?> lineBreakpoint) {
      var type = (XLineBreakpointType)lineBreakpoint.getType();
      return description.equals(type.getDisplayTextDefaultWithPathAndLine(lineBreakpoint));
    }
    return false;
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
