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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class TestMethodWithoutAssertionInspection extends TestMethodWithoutAssertionInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final ListTable table = new ListTable(
      new ListWrappingTableModel(Arrays.asList(methodMatcher.getClassNames(), methodMatcher.getMethodNamePatterns()), "Assertion class name",
                                 InspectionGadgetsBundle.message("method.name.regex")));
    final JPanel tablePanel = UiUtils.createAddRemoveTreeClassChooserPanel(table, "Choose assertion class");
    final CheckBox checkBox =
      new CheckBox(InspectionGadgetsBundle.message("assert.keyword.is.considered.an.assertion"), this, "assertKeywordIsAssertion");
    panel.add(tablePanel, BorderLayout.CENTER);
    panel.add(checkBox, BorderLayout.SOUTH);
    return panel;
  }
}