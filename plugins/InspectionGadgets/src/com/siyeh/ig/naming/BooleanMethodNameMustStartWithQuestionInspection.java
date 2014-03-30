/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.ui.UiUtils;

import javax.swing.*;
import java.awt.*;

public class BooleanMethodNameMustStartWithQuestionInspection extends BooleanMethodNameMustStartWithQuestionInspectionBase {

  public BooleanMethodNameMustStartWithQuestionInspection() {
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final ListTable table = new ListTable(new ListWrappingTableModel(questionList, InspectionGadgetsBundle
      .message("boolean.method.name.must.start.with.question.table.column.name")));
    final JPanel tablePanel = UiUtils.createAddRemovePanel(table);

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(tablePanel, constraints);

    final CheckBox checkBox1 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.methods.with.boolean.return.type.option"), this, "ignoreBooleanMethods");
    constraints.gridy = 1;
    constraints.weighty = 0.0;
    panel.add(checkBox1, constraints);

    final CheckBox checkBox2 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.boolean.methods.in.an.interface.option"), this, "ignoreInAnnotationInterface");
    constraints.gridy = 2;
    panel.add(checkBox2, constraints);

    final CheckBox checkBox3 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.methods.overriding.super.method"), this, "onlyWarnOnBaseMethods");
    constraints.gridy = 3;
    panel.add(checkBox3, constraints);
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }
}
