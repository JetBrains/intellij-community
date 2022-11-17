// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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

  private static final CallMatcher ASSERT_ARGUMENT_COMPARISON = CallMatcher.anyOf(
    CallMatcher.staticCall("org.junit.Assert", "assertEquals", "assertArrayEquals", "assertIterableEquals",
                           "assertLinesMatch", "assertNotEquals"),
    CallMatcher.staticCall("org.junit.jupiter.api.Assertions", "assertEquals", "assertArrayEquals", "assertIterableEquals",
                           "assertLinesMatch", "assertNotEquals")
  );

  private static final CallMatcher ASSERT_ARGUMENTS_THE_SAME = CallMatcher.anyOf(
    CallMatcher.staticCall("org.junit.Assert", "assertSame", "assertNotSame"),
    CallMatcher.staticCall("org.junit.jupiter.api.Assertions", "assertSame", "assertNotSame")
  );


  private static final CallMatcher ONE_ARGUMENT_COMPARISON = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "equals"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "equalsIgnoreCase", "compareToIgnoreCase"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_COMPARABLE, "compareTo")
  );

  private static final CallMatcher ASSERTJ_COMPARISON =
    CallMatcher.instanceCall("org.assertj.core.api.AbstractAssert", "isEqualTo", "isNotEqualTo").parameterCount(1);


  private static final CallMatcher ASSERTJ_THE_SAME =
    CallMatcher.instanceCall("org.assertj.core.api.AbstractAssert", "isSameAs", "isNotSameAs").parameterCount(1);


  private static final CallMatcher ASSERTJ_ASSERT_THAT =
    CallMatcher.staticCall("org.assertj.core.api.Assertions", "assertThat").parameterCount(1);


  @SuppressWarnings("PublicField")
  public boolean ignoreNonFinalClasses = false;

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("equals.with.itself.option"),
                                          this, "ignoreNonFinalClasses");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.with.itself.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsWithItselfVisitor();
  }

  private class EqualsWithItselfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (checkArgumentFinalLibraryClassOrPrimitives(expression) && isEqualsWithItself(expression)) {
        registerMethodCallError(expression);
      }
    }

    private boolean checkArgumentFinalLibraryClassOrPrimitives(PsiMethodCallExpression expression) {
      if (!ignoreNonFinalClasses) return true;
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList.getExpressionCount() < 1) return false;
      PsiType type = argumentList.getExpressions()[0].getType();
      if (type == null) return false;

      if (TypeConversionUtil.isPrimitiveAndNotNull(type)) return true;

      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (!LibraryUtil.classIsInLibrary(aClass)) return false;
      return aClass.hasModifierProperty(PsiModifier.FINAL);
    }
  }

  public static boolean isEqualsWithItself(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpressionList argumentList = expression.getArgumentList();
    final int count = argumentList.getExpressionCount();
    if (count == 1) {
      final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      if (ONE_ARGUMENT_COMPARISON.test(expression)) {
        return isItself(qualifier, argument);
      }
      else if (ASSERTJ_COMPARISON.test(expression)) {
        return isItself(getAssertThatArgument(expression), argument);
      }
      else if (ASSERTJ_THE_SAME.test(expression)) {
        return isTheSame(getAssertThatArgument(expression), argument);
      }
    }
    else if (count == 2 || count == 3) {
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression firstArgument = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      final PsiExpression secondArgument = PsiUtil.skipParenthesizedExprDown(arguments[1]);

      if (TWO_ARGUMENT_COMPARISON.test(expression) || ASSERT_ARGUMENT_COMPARISON.test(expression)) {
        return isItself(firstArgument, secondArgument);
      }
      else if (ASSERT_ARGUMENTS_THE_SAME.test(expression)) {
        return isTheSame(firstArgument, secondArgument);
      }
    }
    return false;
  }

  @Nullable
  private static PsiExpression getAssertThatArgument(@Nullable PsiExpression expression) {
    while (expression instanceof PsiMethodCallExpression callExpression) {
      if (ASSERTJ_ASSERT_THAT.test(callExpression)) return callExpression.getArgumentList().getExpressions()[0];
      PsiReferenceExpression reference = callExpression.getMethodExpression();
      expression = reference.getQualifierExpression();
      expression = PsiUtil.skipParenthesizedExprDown(expression);
    }
    return null;
  }

  private static boolean isTheSame(@Nullable PsiExpression left, @Nullable PsiExpression right) {
    left = PsiUtil.skipParenthesizedExprDown(left);
    right = PsiUtil.skipParenthesizedExprDown(right);
    if (!(left instanceof PsiReferenceExpression leftReference && right instanceof PsiReferenceExpression rightReference)) {
      return false;
    }
    PsiElement resolvedFromLeft = leftReference.resolve();
    PsiElement resolvedFromRight = rightReference.resolve();
    return resolvedFromLeft!=null && resolvedFromLeft == resolvedFromRight;
  }

  private static boolean isItself(@Nullable PsiExpression left, @Nullable PsiExpression right) {
    return left != null && right != null &&
           EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(left, right) &&
           !SideEffectChecker.mayHaveSideEffects(left);
  }
}
