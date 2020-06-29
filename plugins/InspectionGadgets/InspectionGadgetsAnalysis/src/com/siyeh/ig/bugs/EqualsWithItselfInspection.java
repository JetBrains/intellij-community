// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class EqualsWithItselfInspection extends BaseInspection {

  private static final CallMatcher TWO_ARGUMENT_COMPARISON = CallMatcher.anyOf(
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OBJECTS, "equals", "deepEquals"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COMPARATOR, "compare"),
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_ARRAYS, "equals", "deepEquals"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "compare", "compareUnsigned"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "compare", "compareUnsigned"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_SHORT, "compare", "compareUnsigned"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_BYTE, "compare", "compareUnsigned"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_BOOLEAN, "compare"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_CHARACTER, "compare"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "compare"),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "compare")
  );

  private static final CallMatcher ONE_ARGUMENT_COMPARISON = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "equals"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "equalsIgnoreCase", "compareToIgnoreCase"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_COMPARABLE, "compareTo")
  );

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.with.itself.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsWithItselfVisitor();
  }

  private static class EqualsWithItselfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (isEqualsWithItself(expression)) {
        registerMethodCallError(expression);
      }
    }
  }

  public static boolean isEqualsWithItself(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpressionList argumentList = expression.getArgumentList();
    final int count = argumentList.getExpressionCount();
    if (count == 1) {
      if (ONE_ARGUMENT_COMPARISON.test(expression)) {
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier != null) {
          return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(qualifier, argument) &&
                 !SideEffectChecker.mayHaveSideEffects(qualifier);
        }
        return argument instanceof PsiThisExpression;
      }
    }
    else if (count == 2) {
      if (TWO_ARGUMENT_COMPARISON.test(expression)) {
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiExpression firstArgument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
        final PsiExpression secondArgument = PsiUtil.skipParenthesizedExprDown(arguments[1]);
        return firstArgument != null &&
               EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(firstArgument, secondArgument) &&
               !SideEffectChecker.mayHaveSideEffects(firstArgument);
      }
    }
    return false;
  }
}
