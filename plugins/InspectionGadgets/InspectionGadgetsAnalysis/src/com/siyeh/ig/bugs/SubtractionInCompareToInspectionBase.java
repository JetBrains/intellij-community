/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.MethodMatcher;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class SubtractionInCompareToInspectionBase extends BaseInspection {

  protected final MethodMatcher methodMatcher;

  public SubtractionInCompareToInspectionBase() {
    methodMatcher = new MethodMatcher()
      .add(CommonClassNames.JAVA_UTIL_COLLECTION, "size")
      .add(CommonClassNames.JAVA_UTIL_MAP, "size")
      .add(CommonClassNames.JAVA_LANG_STRING, "length")
      .add(CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER, "length")
      .finishDefault();
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    methodMatcher.readSettings(node);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    methodMatcher.writeSettings(node);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("subtraction.in.compareto.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("subtraction.in.compareto.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SubtractionInCompareToVisitor();
  }

  private class SubtractionInCompareToVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.MINUS)) {
        return;
      }
      if (isSafeSubtraction(expression)) {
        return;
      }
      final PsiLambdaExpression lambdaExpression =
        PsiTreeUtil.getParentOfType(expression, PsiLambdaExpression.class, true, PsiMember.class);
      if (lambdaExpression != null) {
        final PsiClass functionalInterface = PsiUtil.resolveClassInType(lambdaExpression.getFunctionalInterfaceType());
        if (functionalInterface != null && CommonClassNames.JAVA_UTIL_COMPARATOR.equals(functionalInterface.getQualifiedName())) {
          registerError(expression);
          return;
        }
      }

      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (method == null) {
        return;
      }
      if (MethodUtils.isCompareTo(method)) {
        registerError(expression);
      }
      final PsiClass comparatorClass = ClassUtils.findClass(CommonClassNames.JAVA_UTIL_COMPARATOR, expression);
      if (comparatorClass == null) {
        return;
      }
      final PsiMethod[] methods = comparatorClass.findMethodsByName("compare", false);
      assert methods.length == 1;
      final PsiMethod compareMethod = methods[0];
      if (!PsiSuperMethodUtil.isSuperMethod(method, compareMethod)) {
        return;
      }
      registerError(expression);
    }

    private boolean isSafeSubtraction(PsiPolyadicExpression polyadicExpression) {
      final PsiType type = polyadicExpression.getType();
      if (!(PsiType.INT).equals(type)) {
        return false;
      }
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (operands.length != 2) {
        return false;
      }
      final PsiExpression lhs = operands[0];
      final PsiExpression rhs = operands[1];
      final PsiType lhsType = lhs.getType();
      final PsiType rhsType = rhs.getType();
      if (lhsType == null || rhsType == null) {
        return false;
      }
      if ((PsiType.BYTE.equals(lhsType) || PsiType.SHORT.equals(lhsType) || PsiType.CHAR.equals(lhsType)) &&
          (PsiType.BYTE.equals(rhsType) || PsiType.SHORT.equals(rhsType) || PsiType.CHAR.equals(rhsType))) {
        return true;
      }
      return isSafeOperand(lhs) && isSafeOperand(rhs);
    }

    private boolean isSafeOperand(PsiExpression operand) {
      operand = ParenthesesUtils.stripParentheses(operand);
      if (operand instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)operand;
        return methodMatcher.matches(methodCallExpression);
      }
      else if (operand instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)operand;
        final String name = referenceExpression.getReferenceName();
        if (!"length".equals(name)) {
          return false;
        }
        final PsiExpression qualifier = ParenthesesUtils.stripParentheses(referenceExpression.getQualifierExpression());
        if (!(qualifier instanceof PsiReferenceExpression)) {
          return false;
        }
        final PsiType type = qualifier.getType();
        return type instanceof PsiArrayType;
      }
      return false;
    }
  }
}