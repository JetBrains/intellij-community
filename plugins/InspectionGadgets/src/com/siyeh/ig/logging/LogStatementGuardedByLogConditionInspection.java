/*
 * Copyright 2008-2013 Bas Leijdekkers
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
package com.siyeh.ig.logging;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.util.ui.CheckBox;
import com.intellij.util.ui.FormBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.TextField;
import com.siyeh.ig.ui.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class LogStatementGuardedByLogConditionInspection extends LogStatementGuardedByLogConditionInspectionBase {

  public LogStatementGuardedByLogConditionInspection() {
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final JLabel classNameLabel = new JLabel(InspectionGadgetsBundle.message("logger.name.option"));
    classNameLabel.setHorizontalAlignment(SwingConstants.TRAILING);
    final TextField loggerClassNameField = new TextField(this, "loggerClassName");
    final ListTable table = new ListTable(new ListWrappingTableModel(Arrays.asList(logMethodNameList, logConditionMethodNameList),
                                                                     InspectionGadgetsBundle.message("log.method.name"),
                                                                     InspectionGadgetsBundle.message("log.condition.text")));
    panel.add(UiUtils.createAddRemovePanel(table), BorderLayout.CENTER);
    panel.add(FormBuilder.createFormBuilder().addLabeledComponent(classNameLabel, loggerClassNameField).getPanel(), BorderLayout.NORTH);
    panel.add(new CheckBox(InspectionGadgetsBundle.message("log.statement.guarded.by.log.condition.flag.all.unguarded.option"),
                           this, "flagAllUnguarded"), BorderLayout.SOUTH);
    return panel;
  }
}
