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
package com.siyeh.ig.methodmetrics;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.FormBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ParametersPerConstructorInspection extends MethodMetricInspection {

  @SuppressWarnings("PublicField") public Scope ignoreScope = Scope.NONE;

  @Override
  public JComponent createOptionsPanel() {
    final JFormattedTextField valueField = prepareNumberEditor("m_limit");
    final JComboBox<Scope> comboBox = new ComboBox<>(new Scope[] {Scope.NONE, Scope.PRIVATE, Scope.PACKAGE_LOCAL, Scope.PROTECTED});
    comboBox.setRenderer(SimpleListCellRenderer.create("", Scope::getText));
    comboBox.setSelectedItem(ignoreScope);
    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ignoreScope = (Scope)comboBox.getSelectedItem();
      }
    });
    comboBox.setPrototypeDisplayValue(Scope.PROTECTED);

    return new FormBuilder()
      .addLabeledComponent(getConfigurationLabel(), valueField)
      .addLabeledComponent(InspectionGadgetsBundle.message("constructor.visibility.option"), comboBox)
      .addVerticalGap(-1)
      .getPanel();
  }

  @Override
  @NotNull
  public String getID() {
    return "ConstructorWithTooManyParameters";
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
  public BaseInspectionVisitor buildVisitor() {
    return new ParametersPerConstructorVisitor();
  }

  protected enum Scope {
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