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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class IgnoreResultOfCallInspection extends IgnoreResultOfCallInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final ListTable table = new ListTable(new ListWrappingTableModel(
      Arrays.asList(myMethodMatcher.getClassNames(), myMethodMatcher.getMethodNamePatterns()),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.method.column.title")));
    final JPanel tablePanel = UiUtils.createAddRemoveTreeClassChooserPanel(table, "Choose class");
    final CheckBox checkBox =
      new CheckBox(InspectionGadgetsBundle.message("result.of.method.call.ignored.non.library.option"), this, "m_reportAllNonLibraryCalls");
    panel.add(tablePanel, BorderLayout.CENTER);
    panel.add(checkBox, BorderLayout.SOUTH);
    return panel;
  }
}