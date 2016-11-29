/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/**
 * (c) 2016 Silent Forest AB
 * created: 28 April 2016
 */
package com.siyeh.ig.threading;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author Bas Leijdekkers
 */
public class SharedThreadLocalRandomInspection extends SharedThreadLocalRandomInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table = new ListTable(new ListWrappingTableModel(
      Arrays.asList(myMethodMatcher.getClassNames(), myMethodMatcher.getMethodNamePatterns()),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.method.column.title")));
    return UiUtils.createAddRemoveTreeClassChooserPanel(table, "Choose class");
  }
}
