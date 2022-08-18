// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiReplacementUtil {

  /**
   * Consider to use {@link #replaceExpression(PsiExpression, String, CommentTracker)} to preserve comments
   */
  public static void replaceExpression(@NotNull PsiExpression expression, @NotNull @NonNls String newExpressionText) {
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiExpression newExpression = factory.createExpressionFromText(newExpressionText, expression);
    final PsiElement replacementExpression = expression.replace(newExpression);
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    styleManager.reformat(replacementExpression);
  }

  /**
   * @param tracker ensure to {@link CommentTracker#markUnchanged(PsiElement)} expressions used as getText in newExpressionText
   */
  public static void replaceExpression(@NotNull PsiExpression expression, @NotNull @NonNls String newExpressionText, CommentTracker tracker) {
    final Project project = expression.getProject();
    final PsiElement replacementExpression = tracker.replaceAndRestoreComments(expression, newExpressionText);
    CodeStyleManager.getInstance(project).reformat(replacementExpression);
  }

  public static PsiElement replaceExpressionAndShorten(@NotNull PsiExpression expression, @NotNull @NonNls String newExpressionText) {
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiExpression newExpression = factory.createExpressionFromText(newExpressionText, expression);
    final PsiElement replacementExp = expression.replace(newExpression);
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    javaCodeStyleManager.shortenClassReferences(replacementExp);
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    return styleManager.reformat(replacementExp);
  }

  public static PsiElement replaceExpressionAndShorten(@NotNull PsiExpression expression, @NotNull @NonNls String newExpressionText, CommentTracker tracker) {
    final Project project = expression.getProject();
    final PsiElement replacementExp = tracker.replaceAndRestoreComments(expression, newExpressionText);
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    javaCodeStyleManager.shortenClassReferences(replacementExp);
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    return styleManager.reformat(replacementExp);
  }

  /**
   * Consider to use {@link #replaceStatement(PsiStatement, String, CommentTracker)} to preserve comments
   */
  public static PsiElement replaceStatement(@NotNull PsiStatement statement, @NotNull @NonNls String newStatementText) {
    final Project project = statement.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiStatement newStatement = factory.createStatementFromText(newStatementText, statement);
    final PsiElement replacementExp = statement.replace(newStatement);
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    return styleManager.reformat(replacementExp);
  }

  /**
   * @param commentTracker ensure to {@link CommentTracker#markUnchanged(PsiElement)} expressions used as getText in newStatementText
   */
  public static PsiElement replaceStatement(@NotNull PsiStatement statement, @NotNull @NonNls String newStatementText, CommentTracker commentTracker) {
    final Project project = statement.getProject();
    final PsiElement replacementExp = commentTracker.replaceAndRestoreComments(statement, newStatementText);
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    return styleManager.reformat(replacementExp);
  }

  /**
   * Consider to use {@link #replaceStatementAndShortenClassNames(PsiStatement, String, CommentTracker)} to preserve comments
   */
  public static void replaceStatementAndShortenClassNames(@NotNull PsiStatement statement, @NotNull @NonNls String newStatementText) {
    replaceStatementAndShortenClassNames(statement, newStatementText, null);
  }

  /**
   * @param tracker ensure to {@link CommentTracker#markUnchanged(PsiElement)} expressions used as getText in newStatementText
   */
  public static PsiElement replaceStatementAndShortenClassNames(@NotNull PsiStatement statement,
                                                                @NotNull @NonNls String newStatementText,
                                                                @Nullable CommentTracker tracker) {
    final Project project = statement.getProject();
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    final JavaCodeStyleManager javaStyleManager = JavaCodeStyleManager.getInstance(project);

    PsiStatement newStatement;
    if (tracker != null) {
      newStatement = (PsiStatement)tracker.replaceAndRestoreComments(statement, newStatementText);
    }
    else {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      newStatement = (PsiStatement)statement.replace(factory.createStatementFromText(newStatementText, statement));
    }
    return styleManager.reformat(javaStyleManager.shortenClassReferences(newStatement));
  }

  public static void replaceExpressionWithReferenceTo(@NotNull PsiExpression expression, @NotNull PsiMember target) {
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiReferenceExpression newExpression = (PsiReferenceExpression)factory.createExpressionFromText("xxx", expression);
    CommentTracker tracker = new CommentTracker();
    final PsiReferenceExpression replacementExpression = (PsiReferenceExpression)tracker.replaceAndRestoreComments(expression, newExpression);
    final PsiElement element = replacementExpression.bindToElement(target);
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
    styleManager.shortenClassReferences(element);
  }

  @NotNull
  public static String getElementText(@NotNull PsiElement element, @Nullable PsiElement elementToReplace, @Nullable String replacement) {
    final StringBuilder out = new StringBuilder();
    getElementText(element, elementToReplace, replacement, out);
    return out.toString();
  }

  private static void getElementText(@NotNull PsiElement element, @Nullable PsiElement elementToReplace,
                                     @Nullable String replacement, @NotNull StringBuilder out) {
    if (element.equals(elementToReplace)) {
      out.append(replacement);
      return;
    }
    final PsiElement[] children = element.getChildren();
    if (children.length == 0) {
      out.append(element.getText());
      return;
    }
    for (PsiElement child : children) {
      getElementText(child, elementToReplace, replacement, out);
    }
  }

  public static @NotNull PsiElement replaceOperatorAssignmentWithAssignmentExpression(@NotNull PsiAssignmentExpression assignmentExpression) {
    CommentTracker tracker = new CommentTracker();
    final PsiJavaToken sign = assignmentExpression.getOperationSign();
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final PsiExpression rhs = assignmentExpression.getRExpression();
    final String operator = sign.getText();
    final String newOperator = operator.substring(0, operator.length() - 1);
    final String lhsText = tracker.text(lhs);
    final String rhsText = (rhs == null) ? "" : tracker.text(rhs);
    final boolean parentheses = ParenthesesUtils.areParenthesesNeeded(sign, rhs);
    final String cast = getCastString(lhs, rhs);
    final StringBuilder newExpression = new StringBuilder(lhsText);
    newExpression.append('=').append(cast);
    if (!cast.isEmpty()) {
      newExpression.append('(');
    }
    newExpression.append(lhsText).append(newOperator);
    if (parentheses) {
      newExpression.append('(').append(rhsText).append(')');
    }
    else {
      newExpression.append(rhsText);
    }
    if (!cast.isEmpty()) {
      newExpression.append(')');
    }
    final Project project = assignmentExpression.getProject();
    final PsiElement replacementExpression = tracker.replaceAndRestoreComments(assignmentExpression, newExpression.toString());
    return CodeStyleManager.getInstance(project).reformat(replacementExpression);
  }

  private static String getCastString(PsiExpression lhs, PsiExpression rhs) {
    if (lhs == null || rhs == null) {
      return "";
    }
    final PsiType lType = lhs.getType();
    PsiType rType = rhs.getType();
    if (TypeConversionUtil.isNumericType(rType)) {
      rType = TypeConversionUtil.binaryNumericPromotion(lType, rType);
    }
    if (lType == null || rType == null ||
        TypeConversionUtil.isAssignable(lType, rType) || !TypeConversionUtil.areTypesConvertible(lType, rType)) {
      return "";
    }
    return '(' + lType.getCanonicalText() + ')';
  }

  /**
   * Replaces the specified boolean PsiExpression with a negated expression created from the specified string.
   * The expression is negated by surrounding with {@code !(...)} or if already surrounded removes the {@code !(...)}.
   * @param expression  a boolean PsiExpression
   * @param newExpression  text for the new expression, which will be negated/inverted.
   */
  public static void replaceExpressionWithNegatedExpression(@NotNull PsiExpression expression,
                                                            @NotNull String newExpression,
                                                            CommentTracker tracker) {
    PsiExpression expressionToReplace = expression;
    final String expString;
    if (BoolUtils.isNegated(expression)) {
      expressionToReplace = BoolUtils.findNegation(expressionToReplace);
      expString = newExpression;
    }
    else {
      PsiElement parent = expressionToReplace.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        expressionToReplace = (PsiExpression)parent;
        parent = parent.getParent();
      }
      expString = "!(" + newExpression + ')';
    }
    assert expressionToReplace != null;
    replaceExpression(expressionToReplace, expString, tracker);
  }
}
