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
    final ListTable queryNamesTable = new ListTable(new ListWrappingTableModel(queryNames, InspectionGadgetsBundle.message("query.column.name")));
    final JPanel queryNamesPanel = UiUtils.createAddRemovePanel(queryNamesTable);

    final ListTable updateNamesTable = new ListTable(new ListWrappingTableModel(updateNames, InspectionGadgetsBundle.message("update.column.name")));
    final JPanel updateNamesPanel = UiUtils.createAddRemovePanel(updateNamesTable);

    final ListTable ignoredClassesTable = new ListTable(new ListWrappingTableModel(ignoredClasses, InspectionGadgetsBundle.message(
      "ignored.class.names")));
    final JPanel ignoredClassesPanel = UiUtils.createAddRemovePanel(ignoredClassesTable);

    final JPanel namesPanel = new JPanel(new GridLayout(1, 2, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    namesPanel.add(queryNamesPanel);
    namesPanel.add(updateNamesPanel);

    final JPanel panel = new JPanel(new GridLayout(2, 1, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    panel.add(namesPanel);
    panel.add(ignoredClassesPanel);
    return panel;
  }
}
