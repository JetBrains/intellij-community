/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.ui.UiUtils;

import javax.swing.*;
import java.awt.*;

public class BadExceptionDeclaredInspection extends BadExceptionDeclaredInspectionBase {

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiElement context = (PsiElement)infos[0];
    return SuppressForTestsScopeFix.build(this, context);
  }

  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());
    final ListTable table =
      new ListTable(new ListWrappingTableModel(exceptions, InspectionGadgetsBundle.message("exception.class.column.name")));
    final JPanel tablePanel =
      UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.exception.class"), "java.lang.Throwable");
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(tablePanel, constraints);


    final CheckBox checkBox2 =
      new CheckBox(InspectionGadgetsBundle.message("ignore.exceptions.declared.on.library.override.option"), this,
                   "ignoreLibraryOverrides");
    constraints.weighty = 0.0;
    constraints.gridy = 1;
    panel.add(checkBox2, constraints);
    return panel;
  }
}