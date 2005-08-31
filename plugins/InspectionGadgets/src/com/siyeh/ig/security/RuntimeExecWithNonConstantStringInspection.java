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
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class RuntimeExecWithNonConstantStringInspection extends ExpressionInspection {

  public String getID() {
    return "CallToRuntimeExecWithNonConstantString";
  }

  public String getGroupDisplayName() {
    return GroupNames.SECURITY_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new RuntimeExecVisitor();
  }

  private static class RuntimeExecVisitor extends BaseInspectionVisitor {
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression
        .getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"exec".equals(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (!"java.lang.Runtime".equals(className)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] args = argumentList.getExpressions();
      if (args == null || args.length == 0) {
        return;
      }
      final PsiExpression arg = args[0];
      final PsiType type = arg.getType();
      if (type == null) {
        return;
      }
      final String typeText = type.getCanonicalText();
      if (!"java.lang.String".equals(typeText)) {
        return;
      }
      final String stringValue =
        (String)ConstantExpressionUtil.computeCastTo(arg, type);
      if (stringValue != null) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
