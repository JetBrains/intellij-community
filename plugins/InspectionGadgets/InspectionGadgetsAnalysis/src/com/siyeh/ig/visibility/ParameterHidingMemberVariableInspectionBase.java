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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ParameterHidingMemberVariableInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreInvisibleFields = true;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreStaticMethodParametersHidingInstanceFields = false;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreForConstructors = false;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreForPropertySetters = false;

  @SuppressWarnings("PublicField")
  public boolean m_ignoreForAbstractMethods = false;

  @Override
  @NotNull
  public String getID() {
    return "ParameterHidesMemberVariable";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("parameter.hides.member.variable.display.name");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message("parameter.hides.member.variable.problem.descriptor", aClass.getName());
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("parameter.hides.member.variable.ignore.setters.option"),
                             "m_ignoreForPropertySetters");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("parameter.hides.member.variable.ignore.superclass.option"),
                             "m_ignoreInvisibleFields");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("parameter.hides.member.variable.ignore.constructors.option"),
                             "m_ignoreForConstructors");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("parameter.hides.member.variable.ignore.abstract.methods.option"),
                             "m_ignoreForAbstractMethods");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("parameter.hides.member.variable.ignore.static.parameters.option"),
                             "m_ignoreStaticMethodParametersHidingInstanceFields");
    return optionsPanel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ParameterHidingMemberVariableVisitor();
  }

  private class ParameterHidingMemberVariableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitParameter(@NotNull PsiParameter variable) {
      super.visitParameter(variable);
      final PsiElement declarationScope = variable.getDeclarationScope();
      if (!(declarationScope instanceof PsiMethod)) {
        return;
      }
      final PsiMethod method = (PsiMethod)declarationScope;
      if (m_ignoreForConstructors && method.isConstructor()) {
        return;
      }
      if (m_ignoreForAbstractMethods) {
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          return;
        }
      }
      if (m_ignoreForPropertySetters) {
        final String methodName = method.getName();
        final PsiType returnType = method.getReturnType();
        if (methodName.startsWith(HardcodedMethodConstants.SET) && PsiType.VOID.equals(returnType)) {
          return;
        }
      }
      final PsiClass aClass = checkFieldName(variable, method);
      if (aClass ==  null) {
        return;
      }
      registerVariableError(variable, aClass);
    }

    @Nullable
    private PsiClass checkFieldName(PsiVariable variable, PsiMethod method) {
      final String variableName = variable.getName();
      if (variableName == null) {
        return null;
      }
      PsiClass aClass = ClassUtils.getContainingClass(variable);
      while (aClass != null) {
        final PsiField[] fields = aClass.getAllFields();
        for (PsiField field : fields) {
          final String fieldName = field.getName();
          if (!variableName.equals(fieldName)) {
            continue;
          }
          if (m_ignoreStaticMethodParametersHidingInstanceFields && !field.hasModifierProperty(PsiModifier.STATIC) &&
              method.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          if (!m_ignoreInvisibleFields || ClassUtils.isFieldVisible(field, aClass)) {
            return aClass;
          }
        }
        aClass = ClassUtils.getContainingClass(aClass);
      }
      return null;
    }
  }
}