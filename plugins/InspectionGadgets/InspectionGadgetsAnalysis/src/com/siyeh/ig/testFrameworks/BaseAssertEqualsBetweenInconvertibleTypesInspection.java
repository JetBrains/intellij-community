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
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

public abstract class BaseAssertEqualsBetweenInconvertibleTypesInspection extends BaseInspection {
  private static final CallMatcher ASSERTJ_IS_EQUAL = CallMatcher.instanceCall(
    "org.assertj.core.api.Assert", "isEqualTo", "isSameAs", "isNotEqualTo", "isNotSameAs")
    .parameterTypes(CommonClassNames.JAVA_LANG_OBJECT);
  private static final CallMatcher ASSERTJ_DESCRIBED = CallMatcher.instanceCall(
    "org.assertj.core.api.Descriptable", "describedAs", "as");
  private static final CallMatcher ASSERTJ_ASSERT_THAT = CallMatcher.staticCall(
    "org.assertj.core.api.Assertions", "assertThat").parameterCount(1);
  private static final Set<String> ASSERT_NOT_EQUALS_METHODS = new THashSet<>(Arrays.asList(
    "assertNotEquals", "assertNotSame", "isNotEqualTo", "isNotSameAs"));

  protected abstract boolean checkTestNG();

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String methodName = (String)infos[0];
    if (ASSERT_NOT_EQUALS_METHODS.contains(methodName)) {
      return InspectionGadgetsBundle.message("assertnotequals.between.inconvertible.types.problem.descriptor");
    }
    final PsiType comparedType = (PsiType)infos[1];
    final PsiType comparisonType = (PsiType)infos[2];
    return InspectionGadgetsBundle.message("assertequals.between.inconvertible.types.problem.descriptor",
                                           StringUtil.escapeXmlEntities(comparedType.getPresentableText()),
                                           StringUtil.escapeXmlEntities(comparisonType.getPresentableText()));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertEqualsBetweenInconvertibleTypesVisitor();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private class AssertEqualsBetweenInconvertibleTypesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      processAssertEquals(expression);
      processAssertNotEquals(expression);
      processAssertSame(expression);
      processAssertNotSame(expression);
      processAssertJ(expression);
    }

    private void processAssertEquals(@NotNull PsiMethodCallExpression expression) {
      processAssertHint(AssertHint.createAssertEqualsHint(expression, checkTestNG()), expression);
    }

    private void processAssertNotEquals(@NotNull PsiMethodCallExpression expression) {
      processAssertHint(AssertHint.createAssertNotEqualsHint(expression, checkTestNG()), expression);
    }

    private void processAssertSame(@NotNull PsiMethodCallExpression expression) {
      processAssertHint(AssertHint.createAssertSameHint(expression, checkTestNG()), expression);
    }

    private void processAssertNotSame(@NotNull PsiMethodCallExpression expression) {
      processAssertHint(AssertHint.createAssertNotSameHint(expression, checkTestNG()), expression);
    }

    private void processAssertJ(@NotNull PsiMethodCallExpression call) {
      if (!ASSERTJ_IS_EQUAL.test(call)) return;
      PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
      while (ASSERTJ_DESCRIBED.test(qualifierCall)) {
        qualifierCall = MethodCallUtils.getQualifierMethodCall(qualifierCall);
      }
      if (!ASSERTJ_ASSERT_THAT.test(qualifierCall)) return;
      checkConvertibleTypes(call, call.getArgumentList().getExpressions()[0], qualifierCall.getArgumentList().getExpressions()[0]);
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

    private void checkConvertibleTypes(@NotNull PsiMethodCallExpression expression, PsiExpression firstArgument, PsiExpression secondArgument) {
      final PsiType type1 = firstArgument.getType();
      if (type1 == null) return;
      final PsiType type2 = secondArgument.getType();
      if (type2 == null) return;
      InconvertibleTypesChecker.LookForMutualSubclass lookForMutualSubclass =
        isOnTheFly() ? InconvertibleTypesChecker.LookForMutualSubclass.IF_CHEAP : InconvertibleTypesChecker.LookForMutualSubclass.ALWAYS;
      InconvertibleTypesChecker.TypeMismatch mismatch = InconvertibleTypesChecker.checkTypes(type1, type2, lookForMutualSubclass);
      if (mismatch != null) {
        PsiElement name = Objects.requireNonNull(expression.getMethodExpression().getReferenceNameElement());
        registerError(name, name.getText(), mismatch.getLeft(), mismatch.getRight());
      }
    }
  }
}
