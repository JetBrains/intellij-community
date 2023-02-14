/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.junit;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class CreateAssertIntention extends Intention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("create.assert.intention.family.name");
  }

  @Override
  public @NotNull String getText() {
    return IntentionPowerPackBundle.message("create.assert.intention.name");
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new CreateAssertPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiExpressionStatement statement = (PsiExpressionStatement)element;
    final PsiExpression expression = statement.getExpression();
    final String newStatement;
    if (BoolUtils.isNegation(expression)) {
      newStatement = buildNewStatement("assertFalse", element, BoolUtils.getNegatedExpressionText(expression));
    }
    else if (ComparisonUtils.isNullComparison(expression)) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      final PsiExpression comparedExpression = ExpressionUtils.getValueComparedWithNull(binaryExpression);
      assert comparedExpression != null;
      if (JavaTokenType.EQEQ.equals(binaryExpression.getOperationTokenType())) {
        newStatement = buildNewStatement("assertNull", element, comparedExpression.getText());
      }
      else {
        newStatement = buildNewStatement("assertNotNull", element, comparedExpression.getText());
      }
    }
    else if (isEqualityComparison(expression)) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      final PsiExpression comparedExpression;
      final PsiExpression comparingExpression;
      if (rhs instanceof PsiLiteralExpression) {
        comparedExpression = rhs;
        comparingExpression = lhs;
      }
      else {
        comparedExpression = lhs;
        comparingExpression = rhs;
      }
      assert comparingExpression != null;
      final PsiType type = lhs.getType();
      if (PsiTypes.doubleType().equals(type) || PsiTypes.floatType().equals(type)) {
        newStatement = buildNewStatement("assertEquals",
                                          element, comparedExpression.getText(), comparingExpression.getText(), "0.0");
      }
      else if (type instanceof PsiPrimitiveType) {
        newStatement = buildNewStatement("assertEquals", element, comparedExpression.getText(), comparingExpression.getText());
      }
      else {
        newStatement = buildNewStatement("assertSame", element, comparedExpression.getText(), comparingExpression.getText());
      }
    }
    else if (isEqualsExpression(expression)) {
      final PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final PsiExpression comparedExpression = methodExpression.getQualifierExpression();
      assert comparedExpression != null;
      final PsiExpressionList argList = call.getArgumentList();
      final PsiExpression comparingExpression = argList.getExpressions()[0];
      if (comparingExpression instanceof PsiLiteralExpression) {
        newStatement = buildNewStatement("assertEquals", element, comparingExpression.getText(), comparedExpression.getText());
      }
      else {
        newStatement = buildNewStatement("assertEquals", element, comparedExpression.getText(), comparingExpression.getText());
      }
    }
    else {
      newStatement = buildNewStatement("assertTrue", element, expression.getText());
    }
    PsiReplacementUtil.replaceStatementAndShortenClassNames(statement, newStatement);
  }

  @NonNls
  private static String buildNewStatement(@NonNls String memberName, PsiElement context, String... argumentTexts) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    final StringBuilder builder = new StringBuilder(memberName).append('(');
    boolean comma = false;
    for (String argumentText : argumentTexts) {
      if (comma) {
        builder.append(',');
      }
      else {
        comma = true;
      }
      builder.append(argumentText);
    }
    builder.append(')');
    final String text = builder.toString();

    final String qualifier = isJUnit5(context) ? "org.junit.jupiter.api.Assertions" : "org.junit.Assert";
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)factory.createExpressionFromText(text, context);
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (isJUnitMethod(method) || hasStaticImports(context) && ImportUtils.addStaticImport(qualifier, memberName, context)) {
      return text + ';';
    }
    else {
      return qualifier + '.' + text + ';';
    }
  }

  private static boolean isJUnit5(PsiElement context) {
    PsiMethod method = PsiTreeUtil.getParentOfType(context, PsiMethod.class);
    if (method == null) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return false;
    }
    final Set<TestFramework> frameworks = TestFrameworks.detectApplicableFrameworks(aClass);
    return frameworks.stream().anyMatch(framework -> framework.getName().equals("JUnit5") && framework.isTestMethod(method, false));
  }

  private static boolean isJUnitMethod(PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String qualifiedName = containingClass.getQualifiedName();
    return "org.junit.Assert".equals(qualifiedName) || "junit.framework.TestCase".equals(qualifiedName);
  }

  private static boolean hasStaticImports(PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (!(file instanceof PsiJavaFile javaFile)) {
      return false;
    }
    final PsiImportList importList = javaFile.getImportList();
    return importList != null && importList.getImportStaticStatements().length > 0;
  }

  private static boolean isEqualsExpression(PsiExpression expression) {
    if (!(expression instanceof PsiMethodCallExpression call)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"equals".equals(methodName)) {
      return false;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier == null) {
      return false;
    }
    final PsiExpressionList argList = call.getArgumentList();
    final PsiExpression[] expressions = argList.getExpressions();
    return expressions.length == 1 && expressions[0] != null;
  }

  private static boolean isEqualityComparison(PsiExpression expression) {
    if (!(expression instanceof PsiBinaryExpression binaryExpression)) {
      return false;
    }
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    return JavaTokenType.EQEQ.equals(tokenType);
  }
}
