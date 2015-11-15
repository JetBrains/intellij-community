/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.CheckBox;
import com.intellij.util.ui.FormBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class NonBooleanMethodNameMayNotStartWithQuestionInspection extends NonBooleanMethodNameMayNotStartWithQuestionInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final ListTable table = new ListTable(new ListWrappingTableModel(questionList, InspectionGadgetsBundle
      .message("boolean.method.name.must.start.with.question.table.column.name")));
    final JPanel tablePanel = UiUtils.createAddRemovePanel(table);

    final CheckBox checkBox1 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.methods.with.boolean.return.type.option"), this, "ignoreBooleanMethods");
    final CheckBox checkBox2 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.methods.overriding.super.method"), this, "onlyWarnOnBaseMethods");

    panel.add(tablePanel, BorderLayout.CENTER);
    panel.add(FormBuilder.createFormBuilder().addComponent(checkBox1).addComponent(checkBox2).getPanel(), BorderLayout.SOUTH);
    return panel;
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiElement context = (PsiElement)infos[0];
    final InspectionGadgetsFix suppressFix = SuppressForTestsScopeFix.build(this, context);
    if (suppressFix == null) {
      return new InspectionGadgetsFix[] {new RenameFix()};
    }
    return new InspectionGadgetsFix[] {new RenameFix(), suppressFix};
  }
}
