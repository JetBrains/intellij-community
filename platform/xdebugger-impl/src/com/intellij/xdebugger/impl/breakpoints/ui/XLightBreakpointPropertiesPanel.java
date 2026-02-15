// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy;
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy;
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointTypeProxy;
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

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

  public interface Delegate {
    void showActionOptions();
  }

  private final JPanel myConditionPanel;
  private final JPanel myMainPanel;

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

  private final XSuspendPolicyPanel mySuspendPolicyPanel;
  private final XBreakpointActionsPanel myActionsPanel;
  private final XMasterBreakpointPanel myMasterBreakpointPanel;
  private final JPanel myCustomPropertiesPanelWrapper;
  private final JPanel myCustomConditionsPanelWrapper;
  private final JCheckBox myEnabledCheckbox;
  private final JPanel myCustomRightPropertiesPanelWrapper;
  private JBCheckBox myConditionEnabledCheckbox;
  private final JPanel myCustomTopPropertiesPanelWrapper;
  private final JBLabel myBreakpointNameLabel;
  private final JPanel myConditionCheckboxPanel;
  private final JPanel myLanguageChooserPanel;
  private final JPanel myConditionExpressionPanel;
  private final ActionLink myRestoreLink;
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

  private final @NotNull XBreakpointManagerProxy myBreakpointManager;

  public XLightBreakpointPropertiesPanel(Project project, XBreakpointManagerProxy breakpointManager, XBreakpointProxy breakpoint,
                                         boolean showActionOptions, boolean showAllOptions, boolean isEditorBalloon) {
    myBreakpointManager = breakpointManager;
    myBreakpoint = breakpoint;
    myShowAllOptions = showAllOptions;
    myIsEditorBalloon = isEditorBalloon;
    {
      myRestoreLink = new ActionLink(XDebuggerBundle.message("xbreakpoints.restore.label"), e -> {
        myBreakpointManager.restoreRemovedBreakpoint(myBreakpoint);
        if (myBalloon != null) {
          myBalloon.hide();
        }
      });
    }
    {
      // GUI initializer generated by IntelliJ IDEA GUI Designer
      // >>> IMPORTANT!! <<<
      // DO NOT EDIT OR ADD ANY CODE HERE!
      myMainPanel = new JPanel();
      myMainPanel.setLayout(new GridLayoutManager(11, 2, new Insets(0, 0, 0, 0), -1, -1));
      myConditionPanel = new JPanel();
      myConditionPanel.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, 0));
      myMainPanel.add(myConditionPanel, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
      myConditionCheckboxPanel = new JPanel();
      myConditionCheckboxPanel.setLayout(new BorderLayout(0, 0));
      myConditionPanel.add(myConditionCheckboxPanel,
                           new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                               null, 0, false));
      myLanguageChooserPanel = new JPanel();
      myLanguageChooserPanel.setLayout(new BorderLayout(0, 0));
      myConditionPanel.add(myLanguageChooserPanel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                       GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                       GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
      myConditionExpressionPanel = new JPanel();
      myConditionExpressionPanel.setLayout(new BorderLayout(0, 0));
      myConditionPanel.add(myConditionExpressionPanel,
                           new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                               null, 0, false));
      final Spacer spacer1 = new Spacer();
      myConditionPanel.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      mySuspendPolicyPanel = new XSuspendPolicyPanel();
      myMainPanel.add(mySuspendPolicyPanel.$$$getRootComponent$$$(),
                      new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                          false));
      myActionsPanel = new XBreakpointActionsPanel();
      myMainPanel.add(myActionsPanel.$$$getRootComponent$$$(),
                      new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                          false));
      myMasterBreakpointPanel = new XMasterBreakpointPanel();
      myMainPanel.add(myMasterBreakpointPanel.$$$getRootComponent$$$(),
                      new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                          false));
      myCustomPropertiesPanelWrapper = new JPanel();
      myCustomPropertiesPanelWrapper.setLayout(new BorderLayout(0, 0));
      myMainPanel.add(myCustomPropertiesPanelWrapper,
                      new GridConstraints(9, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                          false));
      final Spacer spacer2 = new Spacer();
      myMainPanel.add(spacer2, new GridConstraints(10, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, new Dimension(400, -1), null, 0, false));
      myCustomConditionsPanelWrapper = new JPanel();
      myCustomConditionsPanelWrapper.setLayout(new BorderLayout(0, 0));
      myMainPanel.add(myCustomConditionsPanelWrapper,
                      new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                          false));
      myCustomRightPropertiesPanelWrapper = new JPanel();
      myCustomRightPropertiesPanelWrapper.setLayout(new BorderLayout(0, 0));
      myCustomRightPropertiesPanelWrapper.setEnabled(true);
      myCustomRightPropertiesPanelWrapper.setVisible(true);
      myMainPanel.add(myCustomRightPropertiesPanelWrapper,
                      new GridConstraints(6, 1, 3, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 2,
                                          false));
      myCustomRightPropertiesPanelWrapper.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                         TitledBorder.DEFAULT_POSITION, null, null));
      final Spacer spacer3 = new Spacer();
      myMainPanel.add(spacer3, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                   GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      myCustomTopPropertiesPanelWrapper = new JPanel();
      myCustomTopPropertiesPanelWrapper.setLayout(new BorderLayout(0, 0));
      myMainPanel.add(myCustomTopPropertiesPanelWrapper,
                      new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                          false));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(1, 1, new Insets(5, 0, 0, 0), -1, -1));
      myMainPanel.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
      myEnabledCheckbox = new JCheckBox();
      this.$$$loadButtonText$$$(myEnabledCheckbox,
                                this.$$$getMessageFromBundle$$$("messages/XDebuggerBundle", "xbreakpoints.enabled.label"));
      panel1.add(myEnabledCheckbox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      final JPanel panel2 = new JPanel();
      panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
      myMainPanel.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
      myBreakpointNameLabel = new JBLabel();
      Font myBreakpointNameLabelFont = this.$$$getFont$$$(null, Font.BOLD, -1, myBreakpointNameLabel.getFont());
      if (myBreakpointNameLabelFont != null) myBreakpointNameLabel.setFont(myBreakpointNameLabelFont);
      this.$$$loadLabelText$$$(myBreakpointNameLabel,
                               this.$$$getMessageFromBundle$$$("messages/XDebuggerBundle", "xbreakpoints.breakpoint.name"));
      panel2.add(myBreakpointNameLabel,
                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
      this.$$$loadButtonText$$$(myRestoreLink, this.$$$getMessageFromBundle$$$("messages/XDebuggerBundle", "xbreakpoints.restore.label"));
      panel2.add(myRestoreLink, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
    }
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
    }
    else {
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
    if (customRightConditionPanel != null) {
      XBreakpoint<?> monolithBreakpoint = XDebuggerEntityConverter.getBreakpoint(myBreakpoint.getId());
      if (monolithBreakpoint != null) {
        isVisibleOnPopup = customRightConditionPanel.isVisibleOnPopup(monolithBreakpoint);
      }
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

  /** @noinspection ALL */
  private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    }
    else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      }
      else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac
                            ? new Font(font.getFamily(), font.getStyle(), font.getSize())
                            : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }

  private static Method $$$cachedGetBundleMethod$$$ = null;

  /** @noinspection ALL */
  private String $$$getMessageFromBundle$$$(String path, String key) {
    ResourceBundle bundle;
    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    }
    catch (Exception e) {
      bundle = ResourceBundle.getBundle(path);
    }
    return bundle.getString(key);
  }

  /** @noinspection ALL */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  private void $$$loadButtonText$$$(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /** @noinspection ALL */
  public JComponent $$$getRootComponent$$$() { return myMainPanel; }

  private void onCheckboxChanged() {
    if (myConditionComboBox != null) {
      myConditionComboBox.setEnabled(myConditionEnabledCheckbox.isSelected());
    }
  }

  public void saveProperties() {
    mySubPanels.forEach(XBreakpointPropertiesSubPanel::saveProperties);

    if (myConditionComboBox != null) {
      XExpression expression = myConditionComboBox.getExpression();
      XExpression condition = !DebuggerUIUtil.isEmptyExpression(expression) ? expression : null;
      myBreakpoint.setConditionEnabled(condition == null || myConditionEnabledCheckbox.isSelected());
      myBreakpoint.setConditionExpression(condition);
      myConditionComboBox.saveTextInHistory();
    }

    XBreakpoint<?> monolithBreakpoint = XDebuggerEntityConverter.getBreakpoint(myBreakpoint.getId());
    if (monolithBreakpoint != null) {
      for (XBreakpointCustomPropertiesPanel customPanel : myCustomPanels) {
        customPanel.saveTo(monolithBreakpoint);
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

    XBreakpoint<?> monolithBreakpoint = XDebuggerEntityConverter.getBreakpoint(myBreakpoint.getId());
    if (monolithBreakpoint != null) {
      for (XBreakpointCustomPropertiesPanel customPanel : myCustomPanels) {
        customPanel.loadFrom(monolithBreakpoint);
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
