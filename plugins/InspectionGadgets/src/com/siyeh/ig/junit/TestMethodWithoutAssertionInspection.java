/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.util.ui.CheckBox;
import com.intellij.util.ui.FormBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodMatcher;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.ui.UiUtils;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

public class TestMethodWithoutAssertionInspection extends BaseInspection {

  protected final MethodMatcher methodMatcher;
  @SuppressWarnings("PublicField") public boolean assertKeywordIsAssertion;
  @SuppressWarnings("PublicField") public boolean ignoreIfExceptionThrown;

  public TestMethodWithoutAssertionInspection() {
    methodMatcher = new MethodMatcher(false, "assertionMethods")
      .add(JUnitCommonClassNames.ORG_JUNIT_ASSERT, "assert.*|fail.*")
      .add(JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT, "assert.*|fail.*")
      .add(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS, "assert.*|fail.*")
      .add("org.assertj.core.api.Assertions", "assertThat")
      .add("org.assertj.core.api.WithAssertions", "assertThat")
      .add("com.google.common.truth.Truth", "assert.*")
      .add("com.google.common.truth.Truth8", "assert.*")
      .add("org.mockito.Mockito", "verify.*")
      .add("org.mockito.InOrder", "verify")
      .add("org.junit.rules.ExpectedException", "expect.*")
      .add("org.hamcrest.MatcherAssert", "assertThat")
      .add("mockit.Verifications", "Verifications")
      .finishDefault();
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListTable table = new ListTable(
      new ListWrappingTableModel(Arrays.asList(methodMatcher.getClassNames(), methodMatcher.getMethodNamePatterns()),
                                 InspectionGadgetsBundle.message("column.assertion.class.name"),
                                 InspectionGadgetsBundle.message("method.name.regex")));
    final CheckBox checkBox1 =
      new CheckBox(InspectionGadgetsBundle.message("assert.keyword.is.considered.an.assertion"), this, "assertKeywordIsAssertion");
    final CheckBox checkBox2 =
      new CheckBox(InspectionGadgetsBundle.message("inspection.test.method.without.assertions.exceptions.option"), this, "ignoreIfExceptionThrown");
    final String title = InspectionGadgetsBundle.message("test.without.assertion.options.choose.class");
    return new FormBuilder()
      .addComponentFillVertically(UiUtils.createAddRemoveTreeClassChooserPanel(table, title), 0)
      .addComponent(checkBox1)
      .addComponent(checkBox2)
      .getPanel();
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "JUnitTestMethodWithNoAssertions";
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