/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;

import javax.swing.*;

public class MalformedFormatStringInspection extends MalformedFormatStringInspectionBase {
  @Override
  public JComponent createOptionsPanel() {
    ListWrappingTableModel classTableModel =
      new ListWrappingTableModel(classNames, InspectionGadgetsBundle.message("string.format.class.column.name"));
    JPanel classChooserPanel = UiUtils
      .createAddRemoveTreeClassChooserPanel(new ListTable(classTableModel), InspectionGadgetsBundle.message("string.format.choose.class"));

    ListWrappingTableModel methodTableModel =
      new ListWrappingTableModel(methodNames, InspectionGadgetsBundle.message("string.format.class.method.name"));
    JPanel methodPanel = UiUtils.createAddRemovePanel(new ListTable(methodTableModel));

    final JPanel panel = new JPanel();
    BoxLayout boxLayout = new BoxLayout(panel, BoxLayout.Y_AXIS);
    panel.setLayout(boxLayout);

    panel.add(classChooserPanel);
    panel.add(methodPanel);
    return panel;
  }
}
