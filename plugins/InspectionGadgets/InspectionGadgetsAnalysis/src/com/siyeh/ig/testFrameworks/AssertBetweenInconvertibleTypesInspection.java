// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.testFrameworks;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.InconvertibleTypesChecker;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static com.intellij.codeInspection.ProblemHighlightType.WEAK_WARNING;

public class AssertBetweenInconvertibleTypesInspection extends BaseInspection {
  private static final CallMatcher ASSERTJ_IS_EQUAL = CallMatcher.instanceCall(
    "org.assertj.core.api.Assert", "isEqualTo", "isSameAs", "isNotEqualTo", "isNotSameAs")
    .parameterCount(1);
  private static final CallMatcher ASSERTJ_DESCRIBED = CallMatcher.instanceCall(
    "org.assertj.core.api.Descriptable", "describedAs", "as");
  private static final CallMatcher ASSERTJ_ASSERT_THAT = CallMatcher.staticCall(
    "org.assertj.core.api.Assertions", "assertThat").parameterCount(1);

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String methodName = (String)infos[0];
    final String comparedTypeText = ((PsiType)infos[1]).getPresentableText();
    final String comparisonTypeText = ((PsiType)infos[2]).getPresentableText();
    if (isAssertNotEqualsMethod(methodName)) {
      return InspectionGadgetsBundle.message("assertnotequals.between.inconvertible.types.problem.descriptor", comparedTypeText, comparisonTypeText);
    }
    if (isAssertNotSameMethod(methodName)) {
      return InspectionGadgetsBundle.message("assertnotsame.between.inconvertible.types.problem.descriptor", comparedTypeText, comparisonTypeText);
    }
    return InspectionGadgetsBundle.message("assertequals.between.inconvertible.types.problem.descriptor",
                                           StringUtil.escapeXmlEntities(comparedTypeText),
                                           StringUtil.escapeXmlEntities(comparisonTypeText));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertEqualsBetweenInconvertibleTypesVisitor();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static class AssertEqualsBetweenInconvertibleTypesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      processAssertHint(AssertHint.createAssertEqualsHint(expression), expression);
      processAssertHint(AssertHint.createAssertNotEqualsHint(expression), expression);
      processAssertHint(AssertHint.createAssertSameHint(expression), expression);
      processAssertHint(AssertHint.createAssertNotSameHint(expression), expression);
      processAssertJ(expression);
    }

    private void processAssertJ(@NotNull PsiMethodCallExpression call) {
      getAssertThatMethodCall(call).ifPresent(assertThatCall -> checkConvertibleTypes(call, call.getArgumentList().getExpressions()[0],
                                                                                      assertThatCall.getArgumentList().getExpressions()[0]));
    }

    private void processAssertHint(@Nullable AssertHint assertHint, @NotNull PsiMethodCallExpression expression) {
      if (assertHint == null) return;
      PsiExpression firstArgument = assertHint.getFirstArgument();
      PsiExpression secondArgument = assertHint.getSecondArgument();
      PsiParameter firstParameter = MethodCallUtils.getParameterForArgument(firstArgument);
      if (firstParameter == null || !TypeUtils.isJavaLangObject(firstParameter.getType())) return;
      PsiParameter secondParameter = MethodCallUtils.getParameterForArgument(secondArgument);
      if (secondParameter == null || !TypeUtils.isJavaLangObject(secondParameter.getType())) return;
      checkConvertibleTypes(expression, firstArgument, secondArgument);
    }

    private void checkConvertibleTypes(@NotNull PsiMethodCallExpression expression, @NotNull PsiExpression firstArgument, @NotNull PsiExpression secondArgument) {
      final PsiType type1 = firstArgument.getType();
      if (type1 == null) return;
      final PsiType type2 = secondArgument.getType();
      if (type2 == null) return;
      InconvertibleTypesChecker.LookForMutualSubclass lookForMutualSubclass =
        isOnTheFly() ? InconvertibleTypesChecker.LookForMutualSubclass.IF_CHEAP : InconvertibleTypesChecker.LookForMutualSubclass.ALWAYS;
      InconvertibleTypesChecker.TypeMismatch mismatch = InconvertibleTypesChecker.checkTypes(type1, type2, lookForMutualSubclass);
      if (mismatch != null) {
        PsiElement name = Objects.requireNonNull(expression.getMethodExpression().getReferenceNameElement());
        String methodName = name.getText();
        registerError(name, isAssertNotEqualsMethod(methodName) ? WEAK_WARNING : GENERIC_ERROR_OR_WARNING, methodName, mismatch.getLeft(), mismatch.getRight());
      }
    }
  }

  static Optional<PsiMethodCallExpression> getAssertThatMethodCall(@NotNull PsiMethodCallExpression call) {
    if (!ASSERTJ_IS_EQUAL.test(call)) return Optional.empty();
    PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
    while (ASSERTJ_DESCRIBED.test(qualifierCall)) {
      qualifierCall = MethodCallUtils.getQualifierMethodCall(qualifierCall);
    }
    if (!ASSERTJ_ASSERT_THAT.test(qualifierCall)) return Optional.empty();
    return Optional.of(qualifierCall);
  }

  private static boolean isAssertNotEqualsMethod(@NotNull String methodName) {
    return "assertNotEquals".equals(methodName) || "isNotEqualTo".equals(methodName);
  }

  private static boolean isAssertNotSameMethod(@NotNull String methodName) {
    return "assertNotSame".equals(methodName) || "isNotSameAs".equals(methodName);
  }
}
