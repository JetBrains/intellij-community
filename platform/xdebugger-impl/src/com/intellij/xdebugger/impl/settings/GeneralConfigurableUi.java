// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class GeneralConfigurableUi implements ConfigurableUi<XDebuggerGeneralSettings> {
  private JPanel rootPanel;
  private JCheckBox hideDebugWindowCheckBox;
  private JCheckBox focusApplicationOnBreakpointCheckBox;
  private JCheckBox myShowDebugWindowOnCheckBox;
  private JCheckBox myScrollExecutionPointToCheckBox;
  private JRadioButton myClickRadioButton;
  private JRadioButton myDragToTheEditorRadioButton;
  private JCheckBox myConfirmBreakpointRemoval;
  private JCheckBox myRunToCursorGesture;

  GeneralConfigurableUi() {
    myShowDebugWindowOnCheckBox.addActionListener(e -> updateControls());
  }

  private void updateControls() {
    UIUtil.setEnabled(focusApplicationOnBreakpointCheckBox, myShowDebugWindowOnCheckBox.isSelected(), false);
  }

  @Override
  public void reset(@NotNull XDebuggerGeneralSettings settings) {
    focusApplicationOnBreakpointCheckBox.setSelected(Registry.is("debugger.mayBringFrameToFrontOnBreakpoint"));
    hideDebugWindowCheckBox.setSelected(settings.isHideDebuggerOnProcessTermination());
    myShowDebugWindowOnCheckBox.setSelected(settings.isShowDebuggerOnBreakpoint());
    myScrollExecutionPointToCheckBox.setSelected(settings.isScrollToCenter());
    myClickRadioButton.setSelected(!Registry.is("debugger.click.disable.breakpoints"));
    myDragToTheEditorRadioButton.setSelected(Registry.is("debugger.click.disable.breakpoints"));
    myConfirmBreakpointRemoval.setSelected(settings.isConfirmBreakpointRemoval());
    myRunToCursorGesture.setSelected(settings.isRunToCursorGestureEnabled());
    updateControls();
  }

  @Override
  public boolean isModified(@NotNull XDebuggerGeneralSettings settings) {
    return focusApplicationOnBreakpointCheckBox.isSelected() != Registry.is("debugger.mayBringFrameToFrontOnBreakpoint") ||
           hideDebugWindowCheckBox.isSelected() != settings.isHideDebuggerOnProcessTermination() ||
           myShowDebugWindowOnCheckBox.isSelected() != settings.isShowDebuggerOnBreakpoint() ||
           myScrollExecutionPointToCheckBox.isSelected() != settings.isScrollToCenter() ||
           myDragToTheEditorRadioButton.isSelected() != Registry.is("debugger.click.disable.breakpoints") ||
           myConfirmBreakpointRemoval.isSelected() != settings.isConfirmBreakpointRemoval() ||
           myRunToCursorGesture.isSelected() != settings.isRunToCursorGestureEnabled();
  }

  @Override
  public void apply(@NotNull XDebuggerGeneralSettings settings) {
    Registry.get("debugger.mayBringFrameToFrontOnBreakpoint").setValue(focusApplicationOnBreakpointCheckBox.isSelected());
    settings.setHideDebuggerOnProcessTermination(hideDebugWindowCheckBox.isSelected());
    settings.setShowDebuggerOnBreakpoint(myShowDebugWindowOnCheckBox.isSelected());
    settings.setScrollToCenter(myScrollExecutionPointToCheckBox.isSelected());
    Registry.get("debugger.click.disable.breakpoints").setValue(myDragToTheEditorRadioButton.isSelected());
    settings.setConfirmBreakpointRemoval(myConfirmBreakpointRemoval.isSelected());
    settings.setRunToCursorGestureEnabled(myRunToCursorGesture.isSelected());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return rootPanel;
  }
}