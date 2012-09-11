/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.ui.ListCellRendererWrapper;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ParametersPerConstructorInspection extends MethodMetricInspection {

  private enum Scope {
    NONE {
      @Override
      String getText() {
        return InspectionGadgetsBundle.message("none");
      }
    },
    PRIVATE {
      @Override
      String getText() {
        return InspectionGadgetsBundle.message("private");
      }
    },
    PACKAGE_LOCAL {
      @Override
      String getText() {
        return InspectionGadgetsBundle.message("package.local.private");
      }
    },
    PROTECTED {
      @Override
      String getText() {
        return InspectionGadgetsBundle.message("protected.package.local.private");
      }
    };

    abstract String getText();
  }

  @SuppressWarnings("PublicField") public Scope ignoreScope = Scope.NONE;

  @Override
  @NotNull
  public String getID() {
    return "ConstructorWithTooManyParameters";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("parameters.per.constructor.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer parameterCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message("parameters.per.constructor.problem.descriptor", parameterCount);
  }

  @Override
  protected int getDefaultLimit() {
    return 5;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message("parameter.limit.option");
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel();
    final JLabel textFieldLabel = new JLabel(getConfigurationLabel());
    final JFormattedTextField valueField = prepareNumberEditor("m_limit");
    final JLabel comboBoxLabel = new JLabel(InspectionGadgetsBundle.message("constructor.visibility.option"));
    final JComboBox comboBox = new JComboBox();
    comboBox.addItem(Scope.NONE);
    comboBox.addItem(Scope.PRIVATE);
    comboBox.addItem(Scope.PACKAGE_LOCAL);
    comboBox.addItem(Scope.PROTECTED);
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

    final GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateGaps(true);
    panel.setLayout(layout);
    final GroupLayout.ParallelGroup horizontal = layout.createParallelGroup();
    horizontal.addGroup(layout.createSequentialGroup()
                      .addComponent(textFieldLabel)
                      .addComponent(valueField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
    horizontal.addGroup(layout.createSequentialGroup()
                      .addComponent(comboBoxLabel).addComponent(comboBox, 100, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
    layout.setHorizontalGroup(horizontal);
    final GroupLayout.SequentialGroup vertical = layout.createSequentialGroup();
    vertical.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                      .addComponent(textFieldLabel)
                      .addComponent(valueField));
    vertical.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                      .addComponent(comboBoxLabel)
                      .addComponent(comboBox));
    layout.setVerticalGroup(vertical);

    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ParametersPerConstructorVisitor();
  }

  private class ParametersPerConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      if (!method.isConstructor()) {
        return;
      }
      if (ignoreScope != Scope.NONE) {
        switch (ignoreScope.ordinal()) {
          case 3: if (method.hasModifierProperty(PsiModifier.PROTECTED)) return;
          case 2: if (method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) return;
          case 1: if (method.hasModifierProperty(PsiModifier.PRIVATE)) return;
        }
      }
      final PsiParameterList parameterList = method.getParameterList();
      final int parametersCount = parameterList.getParametersCount();
      if (parametersCount <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(parametersCount));
    }
  }
}