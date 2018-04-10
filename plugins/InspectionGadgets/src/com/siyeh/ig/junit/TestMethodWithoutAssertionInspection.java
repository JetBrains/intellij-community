/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
import com.intellij.util.ui.FormBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class TestMethodWithoutAssertionInspection extends TestMethodWithoutAssertionInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table = new ListTable(
      new ListWrappingTableModel(Arrays.asList(methodMatcher.getClassNames(), methodMatcher.getMethodNamePatterns()), "Assertion class name",
                                 InspectionGadgetsBundle.message("method.name.regex")));
    final CheckBox checkBox1 =
      new CheckBox(InspectionGadgetsBundle.message("assert.keyword.is.considered.an.assertion"), this, "assertKeywordIsAssertion");
    final CheckBox checkBox2 =
      new CheckBox("Ignore test methods which declare exceptions", this, "ignoreIfExceptionThrown");
    return new FormBuilder()
      .addComponentFillVertically(UiUtils.createAddRemoveTreeClassChooserPanel(table, "Choose assertion class"), 0)
      .addComponent(checkBox1)
      .addComponent(checkBox2)
      .getPanel();
  }
}