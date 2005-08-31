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
package com.siyeh.ig.security;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

public class NonFinalCloneInspection extends MethodInspection {

  public String getGroupDisplayName() {
    return GroupNames.SECURITY_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new NonFinalCloneVisitor();
  }

  private static class NonFinalCloneVisitor extends BaseInspectionVisitor {

    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final String name = method.getName();
      if (!HardcodedMethodConstants.CLONE.equals(name)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList == null) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters == null || parameters.length != 0) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.FINAL)
          || method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }

      if (containingClass.hasModifierProperty(PsiModifier.FINAL)
          || containingClass.isInterface()) {
        return;
      }
      registerMethodError(method);
    }
  }
}
