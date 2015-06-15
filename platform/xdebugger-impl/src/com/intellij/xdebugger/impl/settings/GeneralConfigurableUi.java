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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class GeneralConfigurableUi implements ConfigurableUi<XDebuggerGeneralSettings> {
  private JPanel rootPanel;
  private JCheckBox hideDebugWindowCheckBox;
  private JCheckBox focusApplicationOnBreakpointCheckBox;
  private JCheckBox myShowDebugWindowOnCheckBox;
  private JCheckBox myScrollExecutionPointToCheckBox;

  @Override
  public void reset(@NotNull XDebuggerGeneralSettings settings) {
    focusApplicationOnBreakpointCheckBox.setSelected(Registry.is("debugger.mayBringFrameToFrontOnBreakpoint"));
    hideDebugWindowCheckBox.setSelected(settings.isHideDebuggerOnProcessTermination());
    myShowDebugWindowOnCheckBox.setSelected(settings.isShowDebuggerOnBreakpoint());
    myScrollExecutionPointToCheckBox.setSelected(settings.isScrollToCenter());
  }

  @Override
  public boolean isModified(@NotNull XDebuggerGeneralSettings settings) {
    return focusApplicationOnBreakpointCheckBox.isSelected() != Registry.is("debugger.mayBringFrameToFrontOnBreakpoint") ||
           hideDebugWindowCheckBox.isSelected() != settings.isHideDebuggerOnProcessTermination() ||
           myShowDebugWindowOnCheckBox.isSelected() != settings.isShowDebuggerOnBreakpoint() ||
           myScrollExecutionPointToCheckBox.isSelected() != settings.isScrollToCenter();
  }

  @Override
  public void apply(@NotNull XDebuggerGeneralSettings settings) {
    Registry.get("debugger.mayBringFrameToFrontOnBreakpoint").setValue(focusApplicationOnBreakpointCheckBox.isSelected());
    settings.setHideDebuggerOnProcessTermination(hideDebugWindowCheckBox.isSelected());
    settings.setShowDebuggerOnBreakpoint(myShowDebugWindowOnCheckBox.isSelected());
    settings.setScrollToCenter(myScrollExecutionPointToCheckBox.isSelected());
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return rootPanel;
  }
}