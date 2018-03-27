/*
 * Copyright 2011-2017 Bas Leijdekkers
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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ConvertToVarargsMethodFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.siyeh.InspectionGadgetsBundle.message;

public class MethodCanBeVariableArityMethodInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreByteAndShortArrayParameters = false;

  public boolean ignoreAllPrimitiveArrayParameters = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreOverridingMethods = false;

  @SuppressWarnings("PublicField")
  public boolean onlyReportPublicMethods = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreMultipleArrayParameters = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreMultiDimensionalArrayParameters = false;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return message("method.can.be.variable.arity.method.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return message("method.can.be.variable.arity.method.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    final JCheckBox box = panel.addCheckboxEx(message("method.can.be.variable.arity.method.ignore.byte.short.option"), "ignoreByteAndShortArrayParameters");
    panel.addDependentCheckBox(message("method.can.be.variable.arity.method.ignore.all.primitive.arrays.option"), "ignoreAllPrimitiveArrayParameters", box);
    panel.addCheckbox(message("ignore.methods.overriding.super.method"), "ignoreOverridingMethods");
    panel.addCheckbox(message("only.report.public.methods.option"), "onlyReportPublicMethods");
    panel.addCheckbox(message("method.can.be.variable.arity.method.ignore.multiple.arrays.option"), "ignoreMultipleArrayParameters");
    panel.addCheckbox(message("method.can.be.variable.arity.method.ignore.multidimensional.arrays.option"), "ignoreMultiDimensionalArrayParameters");
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
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 0) {
        return;
      }
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      final PsiType type = lastParameter.getType();
      if (!(type instanceof PsiArrayType) || type instanceof PsiEllipsisType) {
        return;
      }
      if (NullableNotNullManager.isNullable(lastParameter)) {
        return;
      }
      final PsiArrayType arrayType = (PsiArrayType)type;
      final PsiType componentType = arrayType.getComponentType();
      if (ignoreMultiDimensionalArrayParameters && componentType instanceof PsiArrayType) {
        // don't report when it is multidimensional array
        return;
      }
      if (ignoreByteAndShortArrayParameters) {
        if (PsiType.BYTE.equals(componentType) || PsiType.SHORT.equals(componentType)) {
          return;
        }
        if (ignoreAllPrimitiveArrayParameters && componentType instanceof PsiPrimitiveType) {
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
      final PsiElement nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      if (isVisibleHighlight(method)) {
        registerErrorAtOffset(method, nameIdentifier.getStartOffsetInParent(), nameIdentifier.getTextLength());
      }
      else {
        final int offset = nameIdentifier.getStartOffsetInParent();
        final int length = parameterList.getStartOffsetInParent() + parameterList.getTextLength() - offset;
        registerErrorAtOffset(method, offset, length);
      }
    }
  }
}
