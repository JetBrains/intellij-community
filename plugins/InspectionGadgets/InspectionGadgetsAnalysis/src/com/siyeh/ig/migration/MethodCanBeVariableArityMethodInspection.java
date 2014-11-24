/*
 * Copyright 2011-2014 Bas Leijdekkers
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
package com.siyeh.ig.migration;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ConvertToVarargsMethodFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MethodCanBeVariableArityMethodInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreByteAndShortArrayParameters = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreOverridingMethods = false;

  @SuppressWarnings("PublicField")
  public boolean onlyReportPublicMethods = false;

  boolean ignoreMultipleArrayParameters = false;

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (Element option : node.getChildren("option")) {
      if ("ignoreMultipleArrayParameters".equals(option.getAttributeValue("name"))) {
        ignoreMultipleArrayParameters = Boolean.parseBoolean(option.getAttributeValue("value"));
      }
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (ignoreMultipleArrayParameters) {
      node.addContent(new Element("option").setAttribute("name", "ignoreMultipleArrayParameters").
        setAttribute("value", String.valueOf(ignoreMultipleArrayParameters)));
    }
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("method.can.be.variable.arity.method.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("method.can.be.variable.arity.method.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("method.can.be.variable.arity.method.ignore.byte.short.option"),
                      "ignoreByteAndShortArrayParameters");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.methods.overriding.super.method"), "ignoreOverridingMethods");
    panel.addCheckbox(InspectionGadgetsBundle.message("only.report.public.methods.option"), "onlyReportPublicMethods");
    panel.addCheckbox(InspectionGadgetsBundle.message("method.can.be.variable.arity.method.ignore.multiple.arrays.option"),
                      "ignoreMultipleArrayParameters");
    return panel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ConvertToVarargsMethodFix();
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodCanBeVariableArityMethodVisitor();
  }

  private class MethodCanBeVariableArityMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (onlyReportPublicMethods && !method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() == 0) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      if (NullableNotNullManager.isNullable(lastParameter)) {
        return;
      }
      final PsiType type = lastParameter.getType();
      if (!(type instanceof PsiArrayType) || type instanceof PsiEllipsisType) {
        return;
      }
      final PsiArrayType arrayType = (PsiArrayType)type;
      final PsiType componentType = arrayType.getComponentType();
      if (componentType instanceof PsiArrayType) {
        // don't report when it is multidimensional array
        return;
      }
      if (ignoreByteAndShortArrayParameters) {
        if (PsiType.BYTE.equals(componentType) || PsiType.SHORT.equals(componentType)) {
          return;
        }
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      if (ignoreOverridingMethods && MethodUtils.hasSuper(method)) {
        return;
      }
      if (ignoreMultipleArrayParameters) {
        for (int i = 0, length = parameters.length - 1; i < length; i++) {
          final PsiParameter parameter = parameters[i];
          if (parameter.getType() instanceof PsiArrayType) {
            return;
          }
        }
      }
      registerMethodError(method);
    }
  }
}
