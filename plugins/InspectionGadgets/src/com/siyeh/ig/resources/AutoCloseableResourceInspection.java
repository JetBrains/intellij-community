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
package com.siyeh.ig.resources;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * @author Bas Leijdekkers
 */
public class AutoCloseableResourceInspection extends AutoCloseableResourceInspectionBase {

  @NotNull
  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new VerticalLayout(2));
    final ListTable table =
      new ListTable(new ListWrappingTableModel(ignoredTypes, InspectionGadgetsBundle.message("ignored.autocloseable.types.column.label")));
    final JPanel tablePanel =
      UiUtils.createAddRemoveTreeClassChooserPanel(table, InspectionGadgetsBundle.message("choose.autocloseable.type.to.ignore.title"),
                                                   "java.lang.AutoCloseable");
    final ListTable table2 = new ListTable(
      new ListWrappingTableModel(Arrays.asList(myMethodMatcher.getClassNames(), myMethodMatcher.getMethodNamePatterns()),
                                 InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
                                 InspectionGadgetsBundle.message("method.name.regex"))) {
      @Override
      public void setEnabled(boolean enabled) {
        // hack to display correctly on initial opening of
        // inspection settings (otherwise it is always enabled)
        super.setEnabled(enabled && !ignoreFromMethodCall);
      }
    };
    final JPanel tablePanel2 = UiUtils.createAddRemoveTreeClassChooserPanel(table2, "Choose class");
    final JPanel wrapperPanel = new JPanel(new BorderLayout());
    wrapperPanel.setBorder(IdeBorderFactory.createTitledBorder("Ignore AutoCloseable instances returned from these methods", false));
    wrapperPanel.add(tablePanel2);
    panel.add(tablePanel);
    panel.add(wrapperPanel);
    final CheckBox checkBox =
      new CheckBox(InspectionGadgetsBundle.message("auto.closeable.resource.returned.option"), this, "ignoreFromMethodCall");
    checkBox.addChangeListener(e -> table2.setEnabled(!ignoreFromMethodCall));
    panel.add(checkBox);
    panel.add(new CheckBox(InspectionGadgetsBundle.message("any.method.may.close.resource.argument"), this, "anyMethodMayClose"));
    return panel;
  }
}
