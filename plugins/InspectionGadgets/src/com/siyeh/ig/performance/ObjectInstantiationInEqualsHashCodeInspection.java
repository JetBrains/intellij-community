// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ObjectInstantiationInEqualsHashCodeInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("object.instantiation.inside.equals.or.hashcode.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiMethod method = PsiTreeUtil.getParentOfType((PsiElement)infos[0], PsiMethod.class);
    assert method != null;
    if (infos.length > 1) {
      return InspectionGadgetsBundle.message("object.instantiation.inside.equals.or.hashcode.problem.descriptor2", method.getName(), infos[1]);
    }
    return InspectionGadgetsBundle.message("object.instantiation.inside.equals.or.hashcode.problem.descriptor", method.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectInstantiationInEqualsHashCodeVisitor();
  }

  private static class ObjectInstantiationInEqualsHashCodeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitExpression(PsiExpression expression) {
      if (!ExpressionUtils.isAutoBoxed(expression) || isAutoBoxingFromCache(expression) || !isInsideEqualsOrHashCode(expression)) {
        return;
      }
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
      if (TypeUtils.getType(CommonClassNames.JAVA_LANG_BOOLEAN, expression).equals(expectedType) ||
          TypeUtils.getType(CommonClassNames.JAVA_LANG_BYTE, expression).equals(expectedType)) {
        return;
      }
      registerError(expression, expression, "autoboxing");
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null || iteratedValue.getType() instanceof PsiArrayType || !isInsideEqualsOrHashCode(statement)) {
        return;
      }
      registerError(iteratedValue, iteratedValue, "iterator");
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      List<StandardMethodContract> contracts = JavaMethodContractUtil.getMethodContracts(method);
      ContractReturnValue contractValue = JavaMethodContractUtil.getNonFailingReturnValue(contracts);
      if (ContractReturnValue.returnNew().equals(contractValue)) {
        if (!isInsideEqualsOrHashCode(expression)) {
          return;
        }
        registerMethodCallError(expression, expression);
      }
      else if (method.isVarArgs()) {
        if (!isInsideEqualsOrHashCode(expression)) {
          return;
        }
        registerMethodCallError(expression, expression, "varargs call");
      }
      else {
        if (!"valueOf".equals(method.getName())) {
          return;
        }
        final PsiExpression[] expressions = expression.getArgumentList().getExpressions();
        if (expressions.length != 1) {
          return;
        }
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
          return;
        }
        final String qualifiedName = aClass.getQualifiedName();
        if (CommonClassNames.JAVA_LANG_SHORT.equals(qualifiedName) ||
            CommonClassNames.JAVA_LANG_INTEGER.equals(qualifiedName) ||
            CommonClassNames.JAVA_LANG_LONG.equals(qualifiedName) ||
            CommonClassNames.JAVA_LANG_CHARACTER.equals(qualifiedName)) {
          if (isAutoBoxingFromCache(expressions[0]) || !isInsideEqualsOrHashCode(expression)) {
            return;
          }
          registerError(expression, expression);
        }
      }
    }

    private static boolean isAutoBoxingFromCache(PsiExpression expression) {
      final LongRangeSet range = CommonDataflow.getExpressionFact(expression, DfaFactType.RANGE);
      if (range != null && !range.isEmpty() && range.min() >= -128 && range.max() <= 127) {
        return true;
      }
      final Object value = ExpressionUtils.computeConstantExpression(expression);
      if (value instanceof Number) {
        final Number number = (Number)value;
        final int l = number.intValue();
        if (l >= -128 && l <= 127) {
          return true;
        }
      }
      else if (value instanceof Character) {
        final Character character = (Character)value;
        final char c = character.charValue();
        if (c <= 127) {
          return true;
        }
      }
      return false;
    }

    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      if (!(expression.getParent() instanceof PsiVariable)) {
        // new expressions are already reported.
        return;
      }
      if (!isInsideEqualsOrHashCode(expression)) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      if (!ExpressionUtils.hasStringType(expression) || ExpressionUtils.isEvaluatedAtCompileTime(expression)) {
        return;
      }
      if (!isInsideEqualsOrHashCode(expression)) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isInsideEqualsOrHashCode(expression)) {
        return;
      }
      registerNewExpressionError(expression, expression);
    }

    private static boolean isInsideEqualsOrHashCode(PsiElement element) {
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiAssertStatement.class, PsiThrowStatement.class);
      if (method == null) {
        return false;
      }
      return MethodUtils.isEquals(method) || MethodUtils.isHashCode(method) ||
             MethodUtils.isCompareTo(method) || MethodUtils.isComparatorCompare(method);
    }
  }
}
