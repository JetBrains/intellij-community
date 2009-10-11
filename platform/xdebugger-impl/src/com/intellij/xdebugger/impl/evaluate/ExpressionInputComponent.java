/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class ExpressionInputComponent extends EvaluationInputComponent {
  private final XDebuggerExpressionComboBox myExpressionComboBox;
  private final JPanel myMainPanel;

  public ExpressionInputComponent(final @NotNull Project project, @NotNull XDebuggerEditorsProvider editorsProvider, final @Nullable XSourcePosition sourcePosition,
                                  @Nullable String expression) {
    super(XDebuggerBundle.message("xdebugger.dialog.title.evaluate.expression"));
    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.add(new JLabel(XDebuggerBundle.message("xdebugger.evaluate.label.expression")), BorderLayout.WEST);
    myExpressionComboBox = new XDebuggerExpressionComboBox(project, editorsProvider, "evaluateExpression", sourcePosition);
    myExpressionComboBox.getComboBox().setMinimumAndPreferredWidth(250);
    myMainPanel.add(myExpressionComboBox.getComponent(), BorderLayout.CENTER);
    if (expression != null) {
      myExpressionComboBox.setText(expression);
    }
    myExpressionComboBox.selectAll();
  }

  @Override
  public void addComponent(JPanel contentPanel, JPanel resultPanel) {
    contentPanel.add(resultPanel, BorderLayout.CENTER);
    contentPanel.add(myMainPanel, BorderLayout.NORTH);
  }

  protected XDebuggerEditorBase getInputEditor() {
    return myExpressionComboBox;
  }
}
