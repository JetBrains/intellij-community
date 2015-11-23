/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * @author Bas Leijdekkers
 */
public class SubtractionInCompareToInspection extends SubtractionInCompareToInspectionBase {

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final ListTable table = new ListTable(
      new ListWrappingTableModel(Arrays.asList(methodMatcher.getClassNames(), methodMatcher.getMethodNamePatterns()),
                                 InspectionGadgetsBundle.message("class.name"),
                                 InspectionGadgetsBundle.message("method.name.regex")));
    final JPanel tablePanel =
      UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.class"));
    panel.add(tablePanel, BorderLayout.CENTER);
    return panel;
  }
}
