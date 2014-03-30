/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.intellij.util.ui.UIUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;

import javax.swing.*;
import java.awt.*;

public class MismatchedCollectionQueryUpdateInspection
  extends MismatchedCollectionQueryUpdateInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridLayout(1, 2, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    final ListTable table1 = new ListTable(new ListWrappingTableModel(queryNames, InspectionGadgetsBundle.message("query.column.name")));
    final JPanel tablePanel1 = UiUtils.createAddRemovePanel(table1);

    final ListTable table2 = new ListTable(new ListWrappingTableModel(updateNames, InspectionGadgetsBundle.message("update.column.name")));
    final JPanel tablePanel2 = UiUtils.createAddRemovePanel(table2);

    panel.add(tablePanel1);
    panel.add(tablePanel2);
    return panel;
  }
}
