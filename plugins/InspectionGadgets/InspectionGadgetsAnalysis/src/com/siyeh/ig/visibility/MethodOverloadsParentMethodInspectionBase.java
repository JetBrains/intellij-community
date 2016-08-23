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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class MethodOverloadsParentMethodInspectionBase extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean reportIncompatibleParameters = false;

  @Override
  @NotNull
  public String getID() {
    return "MethodOverloadsMethodOfSuperclass";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("method.overloads.display.name");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("method.overloads.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("method.overloads.report.incompatible.option"),
                                          this, "reportIncompatibleParameters");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodOverloadsParentMethodVisitor();
  }

  private class MethodOverloadsParentMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (method.getNameIdentifier() == null || method.isConstructor()) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (MethodUtils.hasSuper(method)) {
        return;
      }
      final Set<PsiClass> visitedClasses = new HashSet<>();
      processSupers(method, aClass, aClass, visitedClasses);
    }

    private boolean processSupers(final PsiMethod method,
                                  final PsiClass psiClass,
                                  final PsiClass initialClass,
                                  final Set<PsiClass> visitedClasses) {
      if (!visitedClasses.add(psiClass)) {
        return false;
      }
      if (initialClass != psiClass && methodOverloads(method, psiClass)) {
        registerMethodError(method);
        return true;
      } 
      else {
        if (PsiUtil.isLanguageLevel8OrHigher(method)) {
          for (PsiClass superClass : psiClass.getSupers()) {
            if (processSupers(method, superClass, initialClass, visitedClasses)) {
              return true;
            }
          }
        } else {
          final PsiClass superClass = psiClass.getSuperClass();
          if (superClass != null) {
            return processSupers(method, superClass, initialClass, visitedClasses);
          }
        }
      }
      return false;
    }

    private boolean methodOverloads(PsiMethod method, PsiClass ancestorClass) {
      final String methodName = method.getName();
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiMethod[] methods = ancestorClass.findMethodsByName(methodName, false);
      for (final PsiMethod testMethod : methods) {
        if (!testMethod.hasModifierProperty(PsiModifier.PRIVATE) &&
            !testMethod.hasModifierProperty(PsiModifier.STATIC) &&
            !testMethod.hasModifierProperty(PsiModifier.ABSTRACT) &&
            !isOverriddenInClass(testMethod, method.getContainingClass())) {
          final PsiParameterList testParameterList = testMethod.getParameterList();
          final PsiParameter[] testParameters = testParameterList.getParameters();
          if (testParameters.length == parameters.length) {
            if (reportIncompatibleParameters || parametersAreCompatible(parameters, testParameters)) {
              return true;
            }
          }
        }
      }
      return false;
    }

    private boolean isOverriddenInClass(PsiMethod method, PsiClass aClass) {
      return aClass.findMethodsBySignature(method, false).length > 0;
    }

    private boolean parametersAreCompatible(PsiParameter[] parameters, PsiParameter[] testParameters) {
      for (int i = 0; i < parameters.length; i++) {
        final PsiParameter parameter = parameters[i];
        final PsiType parameterType = parameter.getType();
        final PsiParameter testParameter = testParameters[i];
        final PsiType testParameterType = testParameter.getType();
        if (!parameterType.isAssignableFrom(testParameterType) && !testParameterType.isAssignableFrom(parameterType)) {
          return false;
        }
      }
      return true;
    }
  }
}
