/*
 * Copyright 2006-2015 Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OverloadedVarargsMethodInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreInconvertibleTypes = true;

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "overloaded.vararg.method.problem.option"), this, "ignoreInconvertibleTypes");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiMethod element = (PsiMethod)infos[0];
    if (element.isConstructor()) {
      return InspectionGadgetsBundle.message(
        "overloaded.vararg.constructor.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message(
        "overloaded.vararg.method.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverloadedVarargMethodVisitor();
  }

  private class OverloadedVarargMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!method.isVarArgs()) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String methodName = method.getName();
      final PsiMethod[] sameNameMethods = aClass.findMethodsByName(methodName, true);
      for (PsiMethod sameNameMethod : sameNameMethods) {
        PsiClass superClass = sameNameMethod.getContainingClass();
        PsiSubstitutor substitutor = superClass != null ? TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY)
                                                        : PsiSubstitutor.EMPTY;
        if (!MethodSignatureUtil.areSignaturesEqual(sameNameMethod.getSignature(substitutor),
                                                    method.getSignature(PsiSubstitutor.EMPTY))) {
          if (ignoreInconvertibleTypes && !areConvertibleTypesWithVarArgs(method.getParameterList(),
                                                                          sameNameMethod.getParameterList())) {
            continue;
          }
          registerMethodError(method, method);
          return;
        }
      }
    }

    private static boolean areConvertibleTypesWithVarArgs(@NotNull PsiParameterList parameterListWithVarArgs,
                                                          @NotNull PsiParameterList otherParameterList) {
      PsiParameter[] parametersWithVarArgs = parameterListWithVarArgs.getParameters();
      PsiParameter[] otherParameters = otherParameterList.getParameters();

      int lengthForVarArgs = parametersWithVarArgs.length;
      int otherLength = otherParameters.length;

      //example:
      //parameterListWithVarArgs: (Integer i1, Integer i2, String... strings)
      //otherParameterList: (Integer i1)
      if (lengthForVarArgs > otherLength + 1) {
        return false;
      }

      for (int i = 0; i < otherLength; i++) {
        PsiType type = i < lengthForVarArgs ? getTypeForComparison(parametersWithVarArgs[i]) :
                       getTypeForComparison(parametersWithVarArgs[lengthForVarArgs - 1]);

        PsiType otherType = getTypeForComparison(otherParameters[i]);

        if (!type.isConvertibleFrom(otherType) && !otherType.isConvertibleFrom(type)) {
          return false;
        }
      }

      return true;
    }
  }

  private static PsiType getTypeForComparison(PsiParameter parameter) {
    PsiType type = parameter.getType();
    return type instanceof PsiEllipsisType ellipsisType ? ellipsisType.getComponentType() : type;
  }
}