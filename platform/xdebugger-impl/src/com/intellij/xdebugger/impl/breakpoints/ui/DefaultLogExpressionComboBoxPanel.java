/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author egor
 */
public class DefaultLogExpressionComboBoxPanel<B extends XBreakpoint<?>> extends XBreakpointCustomPropertiesPanel<B> {
  public static final String HISTORY_KEY = "breakpointLogExpression";

  private XDebuggerExpressionComboBox myLogExpressionComboBox;

  public DefaultLogExpressionComboBoxPanel(Project project,
                                           XDebuggerEditorsProvider debuggerEditorsProvider,
                                           XSourcePosition sourcePosition) {
    myLogExpressionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, HISTORY_KEY, sourcePosition);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myLogExpressionComboBox.getComponent();
  }

  @Override
  public void saveTo(@NotNull B breakpoint) {
    String logExpression = myLogExpressionComboBox.getComponent().isEnabled() ? myLogExpressionComboBox.getText() : null;
    breakpoint.setLogExpression(logExpression);
    myLogExpressionComboBox.saveTextInHistory();
  }

  @Override
  public void loadFrom(@NotNull B breakpoint) {
    String logExpression = breakpoint.getLogExpression();
    myLogExpressionComboBox.setText(logExpression != null ? logExpression : "");
  }
}
