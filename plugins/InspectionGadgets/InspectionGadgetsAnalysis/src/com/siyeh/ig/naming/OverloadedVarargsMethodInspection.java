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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class OverloadedVarargsMethodInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "overloaded.vararg.method.display.name");
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

  private static class OverloadedVarargMethodVisitor extends BaseInspectionVisitor {

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
        if (!MethodSignatureUtil.areSignaturesEqual(sameNameMethod, method)) {
          registerMethodError(method, method);
          return;
        }
      }
    }
  }
}