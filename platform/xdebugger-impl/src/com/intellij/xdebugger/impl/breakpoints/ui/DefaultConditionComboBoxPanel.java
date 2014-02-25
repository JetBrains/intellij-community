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
import com.intellij.openapi.util.text.StringUtil;
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
public class DefaultConditionComboBoxPanel<B extends XBreakpoint<?>> extends XBreakpointCustomPropertiesPanel<B> {
  private XDebuggerExpressionComboBox myConditionComboBox;

  public DefaultConditionComboBoxPanel(Project project,
                                       XDebuggerEditorsProvider debuggerEditorsProvider,
                                       String historyId,
                                       XSourcePosition sourcePosition) {
    myConditionComboBox = new XDebuggerExpressionComboBox(project, debuggerEditorsProvider, historyId, sourcePosition);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myConditionComboBox.getComponent();
  }

  @Override
  public void saveTo(@NotNull B breakpoint) {
    final String condition = StringUtil.nullize(myConditionComboBox.getText(), true);
    breakpoint.setCondition(condition);
    if (condition != null) {
      myConditionComboBox.saveTextInHistory();
    }
  }

  @Override
  public void loadFrom(@NotNull B breakpoint) {
    myConditionComboBox.setText(StringUtil.notNullize(breakpoint.getCondition()));
  }
}
