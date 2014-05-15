/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.methodmetrics;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.util.ui.FormBuilder;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ParametersPerConstructorInspection extends ParametersPerConstructorInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final JFormattedTextField valueField = prepareNumberEditor("m_limit");
    final JComboBox comboBox = new ComboBox(new Object[] {Scope.NONE, Scope.PRIVATE, Scope.PACKAGE_LOCAL, Scope.PROTECTED});
    comboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof Scope) setText(((Scope)value).getText());
      }
    });
    comboBox.setSelectedItem(ignoreScope);
    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ignoreScope = (Scope)comboBox.getSelectedItem();
      }
    });
    comboBox.setPrototypeDisplayValue(Scope.PROTECTED);

    final FormBuilder formBuilder = FormBuilder.createFormBuilder();
    formBuilder.addLabeledComponent(getConfigurationLabel(), valueField);
    formBuilder.addLabeledComponent(InspectionGadgetsBundle.message("constructor.visibility.option"), comboBox);
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(formBuilder.getPanel(), BorderLayout.NORTH);
    return panel;
  }
}