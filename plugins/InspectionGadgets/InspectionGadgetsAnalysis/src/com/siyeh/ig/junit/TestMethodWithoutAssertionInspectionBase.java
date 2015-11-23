/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.junit;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.psiutils.MethodMatcher;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class TestMethodWithoutAssertionInspectionBase extends BaseInspection {

  protected final MethodMatcher methodMatcher;
  @SuppressWarnings("PublicField") public boolean assertKeywordIsAssertion;

  public TestMethodWithoutAssertionInspectionBase() {
    methodMatcher = new MethodMatcher(true, "assertionMethods")
      .add("org.junit.Assert", "assert.*|fail.*")
      .add("junit.framework.Assert", "assert.*|fail.*")
      .add("org.mockito.Mockito", "verify.*")
      .add("org.mockito.InOrder", "verify")
      .add("org.junit.rules.ExpectedException", "expect.*")
      .add("org.hamcrest.MatcherAssert", "assertThat")
      .finishDefault();
  }

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
      if (hasExpectedExceptionAnnotation(method)) {
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

    private boolean hasExpectedExceptionAnnotation(PsiMethod method) {
      final PsiModifierList modifierList = method.getModifierList();
      final PsiAnnotation testAnnotation = modifierList.findAnnotation("org.junit.Test");
      if (testAnnotation == null) {
        return false;
      }
      final PsiAnnotationParameterList parameterList = testAnnotation.getParameterList();
      final PsiNameValuePair[] nameValuePairs = parameterList.getAttributes();
      for (PsiNameValuePair nameValuePair : nameValuePairs) {
        @NonNls final String parameterName = nameValuePair.getName();
        if ("expected".equals(parameterName)) {
          return true;
        }
      }
      return false;
    }
  }

  private class ContainsAssertionVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean containsAssertion;

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!containsAssertion) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if (containsAssertion) {
        return;
      }
      super.visitMethodCallExpression(call);
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
