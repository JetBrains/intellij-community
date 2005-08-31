/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryFinalOnParameterInspection extends MethodInspection {

  public String getID() {
    return "UnnecessaryFinalForMethodParameter";
  }

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public String buildErrorString(PsiElement location) {
    final PsiModifierList modifierList = (PsiModifierList)location
      .getParent();
    assert modifierList != null;
    final PsiParameter parameter = (PsiParameter)modifierList.getParent();
    assert parameter != null;
    final String parameterName = parameter.getName();
    return "Unnecessary #ref for parameter " + parameterName + " #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryFinalOnParameterVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return new RemoveModifierFix(location);
  }

  private static class UnnecessaryFinalOnParameterVisitor
    extends BaseInspectionVisitor {
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList == null) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters == null) {
        return;
      }
      for (final PsiParameter parameter : parameters) {
        checkParameter(method, parameter);
      }
    }

    private void checkParameter(PsiMethod method, PsiParameter parameter) {
      if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();

      if (containingClass != null) {
        if (containingClass.isInterface() || containingClass
          .isAnnotationType()) {
          registerModifierError(PsiModifier.FINAL, parameter);
          return;
        }
      }

      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        registerModifierError(PsiModifier.FINAL, parameter);
        return;
      }
      if (VariableAccessUtils.variableIsUsedInInnerClass(parameter, method)) {
        return;
      }
      registerModifierError(PsiModifier.FINAL, parameter);
    }
  }
}
