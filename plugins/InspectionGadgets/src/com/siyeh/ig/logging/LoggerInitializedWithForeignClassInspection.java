/*
 * Copyright 2008-2013 Bas Leijdekkers
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
package com.siyeh.ig.logging;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;

import javax.swing.*;
import java.util.Arrays;


public class LoggerInitializedWithForeignClassInspection extends LoggerInitializedWithForeignClassInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table = new ListTable(
      new ListWrappingTableModel(Arrays.asList(loggerFactoryClassNames, loggerFactoryMethodNames),
                                 InspectionGadgetsBundle.message("logger.factory.class.name"),
                                 InspectionGadgetsBundle.message("logger.factory.method.name")));
    return UiUtils.createAddRemoveTreeClassChooserPanel(table, "Choose logger factory class");
  }
}
