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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NonNls;

public class StringConcatenationInsideStringBufferAppendInspection extends ExpressionInspection {

  private final ReplaceWithChainedAppendFix fix = new ReplaceWithChainedAppendFix();

  public String getGroupDisplayName() {
    return GroupNames.PERFORMANCE_GROUP_NAME;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInsideStringBufferAppendVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class ReplaceWithChainedAppendFix
    extends InspectionGadgetsFix {
    public String getName() {
      return "Replace with chained append() calls";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodNameElement = descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)methodNameElement
        .getParent();
      assert methodExpression != null;
      final PsiMethodCallExpression expression =
        (PsiMethodCallExpression)methodExpression.getParent();
      assert expression != null;
      final PsiExpressionList argList = expression.getArgumentList();
      assert argList != null;
      final PsiExpression[] args = argList.getExpressions();
      final PsiExpression arg = args[0];
      @NonNls final StringBuffer newExpressionBuffer = new StringBuffer();
      final List<PsiExpression> components = findConcatenationComponents(arg);
      final PsiExpression qualifier = methodExpression
        .getQualifierExpression();
      newExpressionBuffer.append(qualifier.getText());
      for (PsiExpression component : components) {
        newExpressionBuffer.append(".append(");
        newExpressionBuffer.append(component.getText());
        newExpressionBuffer.append(')');
      }
      final String newExpression = newExpressionBuffer.toString();
      replaceExpression(expression, newExpression);
    }

    private List<PsiExpression> findConcatenationComponents(
      PsiExpression arg) {
      final List<PsiExpression> out = new ArrayList<PsiExpression>();
      findConcatenationComponents(arg, out);
      return out;
    }

    private void findConcatenationComponents(PsiExpression arg,
                                             List<PsiExpression> out) {
      if (arg instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryArg = (PsiBinaryExpression)arg;
        final PsiExpression lhs = binaryArg.getLOperand();
        findConcatenationComponents(lhs, out);
        final PsiExpression rhs = binaryArg.getROperand();
        out.add(rhs);
      }
      else if (arg instanceof PsiParenthesizedExpression) {
        final PsiExpression contents = ((PsiParenthesizedExpression)arg)
          .getExpression();
        out.add(contents);
      }
      else {
        out.add(arg);
      }
    }
  }

  private static class StringConcatenationInsideStringBufferAppendVisitor
    extends BaseInspectionVisitor {
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression
        .getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"append".equals(methodName)) {
        return;
      }
      final PsiExpressionList argList = expression.getArgumentList();
      if (argList == null) {
        return;
      }
      final PsiExpression[] args = argList.getExpressions();
      if (args.length != 1) {
        return;
      }
      final PsiExpression arg = args[0];
      if (!isConcatenation(arg)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (!"java.lang.StringBuffer".equals(className) &&
          !"java.lang.StringBuilder".equals(className)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean isConcatenation(PsiExpression arg) {
      if (!(arg instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiType type = arg.getType();
      if (type == null) {
        return false;
      }
      final String typeName = type.getCanonicalText();
      return "java.lang.String".equals(typeName);
    }
  }
}
