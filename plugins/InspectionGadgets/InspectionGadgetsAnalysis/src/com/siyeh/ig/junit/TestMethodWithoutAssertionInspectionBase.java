/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.junit;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodMatcher;
import com.siyeh.ig.psiutils.TestUtils;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class TestMethodWithoutAssertionInspectionBase extends BaseInspection {

  protected final MethodMatcher methodMatcher;
  @SuppressWarnings("PublicField") public boolean assertKeywordIsAssertion;
  @SuppressWarnings("PublicField") public boolean ignoreIfExceptionThrown;

  public TestMethodWithoutAssertionInspectionBase() {
    methodMatcher = new MethodMatcher(false, "assertionMethods")
      .add(JUnitCommonClassNames.ORG_JUNIT_ASSERT, "assert.*|fail.*")
      .add(JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT, "assert.*|fail.*")
      .add(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, "assert.*|fail.*")
      .add("org.assertj.core.api.Assertions", "assertThat")
      .add("com.google.common.truth.Truth", "assert.*")
      .add("com.google.common.truth.Truth8", "assert.*")
      .add("org.mockito.Mockito", "verify.*")
      .add("org.mockito.InOrder", "verify")
      .add("org.junit.rules.ExpectedException", "expect.*")
      .add("org.hamcrest.MatcherAssert", "assertThat")
      .add("mockit.Verifications", "Verifications")
      .finishDefault();
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "JUnitTestMethodWithNoAssertions";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("test.method.without.assertion.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("test.method.without.assertion.problem.descriptor");
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    methodMatcher.readSettings(element);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    methodMatcher.writeSettings(element);
    super.writeSettings(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TestMethodWithoutAssertionVisitor();
  }

  private class TestMethodWithoutAssertionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!TestUtils.isJUnitTestMethod(method)) {
        return;
      }
      if (TestUtils.hasExpectedExceptionAnnotation(method)) {
        return;
      }
      if (ignoreIfExceptionThrown && method.getThrowsList().getReferenceElements().length > 0) {
        return;
      }
      if (containsAssertion(method)) {
        return;
      }
      if (lastStatementIsCallToMethodWithAssertion(method)) {
        return;
      }
      registerMethodError(method);
    }

    private boolean lastStatementIsCallToMethodWithAssertion(PsiMethod method) {
      final PsiStatement lastStatement = ControlFlowUtils.getLastStatementInBlock(method.getBody());
      if (!(lastStatement instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)lastStatement;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression != null && !(qualifierExpression instanceof PsiThisExpression)) {
        return false;
      }
      final PsiMethod targetMethod = methodCallExpression.resolveMethod();
      return containsAssertion(targetMethod);
    }

    private boolean containsAssertion(PsiElement element) {
      if (element == null) {
        return false;
      }
      final ContainsAssertionVisitor
        visitor = new ContainsAssertionVisitor();
      element.accept(visitor);
      return visitor.containsAssertion();
    }
  }

  private class ContainsAssertionVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean containsAssertion;

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if ((element instanceof PsiCompiledElement)) {
        // assume no assertions in libraries (prevents assertion in recursive element walking visitor)
        return;
      }
      if (!containsAssertion) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitCallExpression(@NotNull PsiCallExpression call) {
      if (containsAssertion) {
        return;
      }
      super.visitCallExpression(call);
      if (methodMatcher.matches(call)) {
        containsAssertion = true;
      }
    }

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
      if (containsAssertion) {
        return;
      }
      super.visitAssertStatement(statement);
      if (!assertKeywordIsAssertion) {
        return;
      }
      containsAssertion = true;
    }

    boolean containsAssertion() {
      return containsAssertion;
    }
  }
}
