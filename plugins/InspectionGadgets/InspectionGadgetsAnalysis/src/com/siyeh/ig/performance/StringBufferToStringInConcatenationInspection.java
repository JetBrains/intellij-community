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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class StringBufferToStringInConcatenationInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.buffer.to.string.in.concatenation.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final String className = (String)infos[0];
    return InspectionGadgetsBundle.message("string.buffer.to.string.in.concatenation.problem.descriptor", className);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferToStringVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new StringBufferToStringFix();
  }

  private static class StringBufferToStringFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("string.buffer.to.string.in.concatenation.remove.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement methodNameToken = descriptor.getPsiElement();
      final PsiElement methodCallExpression = methodNameToken.getParent();
      assert methodCallExpression != null;
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)methodCallExpression.getParent();
      assert methodCall != null;
      final PsiReferenceExpression expression = methodCall.getMethodExpression();
      final PsiExpression qualifier = expression.getQualifierExpression();
      assert qualifier != null;
      final String newExpression = qualifier.getText();
      PsiReplacementUtil.replaceExpression(methodCall, newExpression);
    }
  }

  private static class StringBufferToStringVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!ExpressionUtils.isStringConcatenationOperand(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.TO_STRING.equals(referenceName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className) && !CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(className)) {
        return;
      }
      registerMethodCallError(expression, aClass.getName());
    }
  }
}