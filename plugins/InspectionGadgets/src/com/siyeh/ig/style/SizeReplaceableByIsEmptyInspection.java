/*
 * Copyright 2006-2012 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class SizeReplaceableByIsEmptyInspection extends SizeReplaceableByIsEmptyInspectionBase {

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new BorderLayout());
    final ListTable table =
      new ListTable(new ListWrappingTableModel(ignoredTypes, InspectionGadgetsBundle.message("ignored.classes.table")));
    JPanel tablePanel =
      UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.class.type.to.ignore"));
    final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message(
      "size.replaceable.by.isempty.negation.ignore.option"), this, "ignoreNegations");
    panel.add(tablePanel, BorderLayout.CENTER);
    panel.add(checkBox, BorderLayout.SOUTH);
    return panel;
  }
}