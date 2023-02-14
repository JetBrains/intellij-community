/*
 * Copyright 2003-2020 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ParenthesesUtils {

  public static final int METHOD_CALL_PRECEDENCE = PsiPrecedenceUtil.METHOD_CALL_PRECEDENCE;
  public static final int POSTFIX_PRECEDENCE = PsiPrecedenceUtil.POSTFIX_PRECEDENCE;
  public static final int PREFIX_PRECEDENCE = PsiPrecedenceUtil.PREFIX_PRECEDENCE;
  public static final int TYPE_CAST_PRECEDENCE = PsiPrecedenceUtil.TYPE_CAST_PRECEDENCE;
  public static final int MULTIPLICATIVE_PRECEDENCE = PsiPrecedenceUtil.MULTIPLICATIVE_PRECEDENCE;
  public static final int ADDITIVE_PRECEDENCE = PsiPrecedenceUtil.ADDITIVE_PRECEDENCE;
  public static final int SHIFT_PRECEDENCE = PsiPrecedenceUtil.SHIFT_PRECEDENCE;
  public static final int EQUALITY_PRECEDENCE = PsiPrecedenceUtil.EQUALITY_PRECEDENCE;
  public static final int BINARY_AND_PRECEDENCE = PsiPrecedenceUtil.BINARY_AND_PRECEDENCE;
  public static final int BINARY_OR_PRECEDENCE = PsiPrecedenceUtil.BINARY_OR_PRECEDENCE;
  public static final int AND_PRECEDENCE = PsiPrecedenceUtil.AND_PRECEDENCE;
  public static final int OR_PRECEDENCE = PsiPrecedenceUtil.OR_PRECEDENCE;
  public static final int CONDITIONAL_PRECEDENCE = PsiPrecedenceUtil.CONDITIONAL_PRECEDENCE;
  public static final int ASSIGNMENT_PRECEDENCE = PsiPrecedenceUtil.ASSIGNMENT_PRECEDENCE;
  public static final int NUM_PRECEDENCES = PsiPrecedenceUtil.NUM_PRECEDENCES;

  private ParenthesesUtils() {}

  public static boolean isCommutativeOperator(@NotNull IElementType token) {
    return PsiPrecedenceUtil.isCommutativeOperator(token);
  }

  public static boolean isCommutativeOperation(PsiPolyadicExpression expression) {
    return PsiPrecedenceUtil.isCommutativeOperation(expression);
  }

  public static boolean isAssociativeOperation(PsiPolyadicExpression expression) {
    return PsiPrecedenceUtil.isAssociativeOperation(expression);
  }

  public static int getPrecedence(PsiExpression expression) {
    return PsiPrecedenceUtil.getPrecedence(expression);
  }

  public static int getPrecedenceForOperator(@NotNull IElementType operator) {
    return PsiPrecedenceUtil.getPrecedenceForOperator(operator);
  }

  public static boolean areParenthesesNeeded(PsiParenthesizedExpression expression, boolean ignoreClarifyingParentheses) {
    return PsiPrecedenceUtil.areParenthesesNeeded(expression, ignoreClarifyingParentheses);
  }

  public static boolean areParenthesesNeeded(PsiExpression expression,
                                             PsiExpression parentExpression,
                                             boolean ignoreClarifyingParentheses) {
    return PsiPrecedenceUtil.areParenthesesNeeded(expression, parentExpression, ignoreClarifyingParentheses);
  }

  public static boolean areParenthesesNeeded(PsiJavaToken compoundAssignmentToken, PsiExpression rhs) {
    return PsiPrecedenceUtil.areParenthesesNeeded(compoundAssignmentToken, rhs);
  }

  public static String getText(@NotNull PsiExpression expression, int precedence) {
    String text = InjectedLanguageManager.getInstance(expression.getProject()).getUnescapedText(expression);
    if (getPrecedence(expression) >= precedence) {
      return '(' + text + ')';
    }
    return text;
  }

  @Nullable public static PsiElement getParentSkipParentheses(PsiElement element) {
    PsiElement parent = element.getParent();
    while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiTypeCastExpression) {
      parent = parent.getParent();
    }
    return parent;
  }

  /**
   * @deprecated use {@link PsiUtil#skipParenthesizedExprDown(PsiExpression)} directly instead
   */
  @Deprecated
  @Contract("null -> null")
  @Nullable
  public static PsiExpression stripParentheses(@Nullable PsiExpression expression) {
    return PsiUtil.skipParenthesizedExprDown(expression);
  }

  public static void removeParentheses(PsiCaseLabelElement element, boolean ignoreClarifyingParentheses) {
    if (element instanceof PsiMethodCallExpression methodCall) {
      removeParensFromMethodCallExpression(methodCall, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiReferenceExpression referenceExpression) {
      removeParensFromReferenceExpression(referenceExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiNewExpression newExpression) {
      removeParensFromNewExpression(newExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiAssignmentExpression assignmentExpression) {
      removeParensFromAssignmentExpression(assignmentExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiArrayInitializerExpression arrayInitializerExpression) {
      removeParensFromArrayInitializerExpression(arrayInitializerExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiTypeCastExpression typeCastExpression) {
      removeParensFromTypeCastExpression(typeCastExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiArrayAccessExpression arrayAccessExpression) {
      removeParensFromArrayAccessExpression(arrayAccessExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiPrefixExpression prefixExpression) {
      removeParensFromPrefixExpression(prefixExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiPostfixExpression postfixExpression) {
      removeParensFromPostfixExpression(postfixExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiPolyadicExpression polyadicExpression) {
      removeParensFromPolyadicExpression(polyadicExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiInstanceOfExpression instanceofExpression) {
      removeParensFromInstanceOfExpression(instanceofExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiConditionalExpression conditionalExpression) {
      removeParensFromConditionalExpression(conditionalExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiParenthesizedExpression parenthesizedExpression) {
      removeParensFromParenthesizedExpression(parenthesizedExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiLambdaExpression lambdaExpression) {
      removeParensFromLambdaExpression(lambdaExpression, ignoreClarifyingParentheses);
    }
    else if (element instanceof PsiPattern pattern) {
      removeParensFromPattern(pattern, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromPattern(PsiPattern pattern, boolean ignoreClarifyingParentheses) {
    if (pattern instanceof PsiParenthesizedPattern parenthesizedPattern) {
      final PsiPattern innerPattern = parenthesizedPattern.getPattern();
      if (innerPattern == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      commentTracker.markUnchanged(innerPattern);
      PsiPattern newPattern = (PsiPattern)commentTracker.replaceAndRestoreComments(parenthesizedPattern, innerPattern);
      removeParentheses(newPattern, ignoreClarifyingParentheses);
    }
    else if (pattern instanceof PsiGuardedPattern guardedPattern) {
      final PsiPrimaryPattern primaryPattern = guardedPattern.getPrimaryPattern();
      removeParentheses(primaryPattern, ignoreClarifyingParentheses);
      final PsiExpression guardingExpression = guardedPattern.getGuardingExpression();
      if (guardingExpression != null) {
        removeParentheses(guardingExpression, ignoreClarifyingParentheses);
      }
    }
    else if (pattern instanceof PsiDeconstructionPattern deconstructionPattern) {
      final PsiPattern[] components = deconstructionPattern.getDeconstructionList().getDeconstructionComponents();
      for (PsiPattern component : components) {
        removeParentheses(component, ignoreClarifyingParentheses);
      }
    }
  }

  private static void removeParensFromLambdaExpression(PsiLambdaExpression lambdaExpression, boolean ignoreClarifyingParentheses) {
    final PsiElement body = lambdaExpression.getBody();
    if (body instanceof PsiExpression expression) {
      removeParentheses(expression, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromReferenceExpression(@NotNull PsiReferenceExpression referenceExpression,
                                                          boolean ignoreClarifyingParentheses) {
    final PsiExpression qualifier = referenceExpression.getQualifierExpression();
    if (qualifier != null) {
      removeParentheses(qualifier, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromParenthesizedExpression(@NotNull PsiParenthesizedExpression parenthesizedExpression,
                                                              boolean ignoreClarifyingParentheses) {
    final PsiExpression body = parenthesizedExpression.getExpression();
    // Do not remove empty parentheses, as incorrect Java expression could become incorrect PSI
    // E.g. ()+=foo is correct PSI, but removing () will yield an assignment without LExpression which is invalid.
    if (body == null) return;
    final PsiElement parent = parenthesizedExpression.getParent();
    if (!(parent instanceof PsiExpression expression) || !areParenthesesNeeded(body, expression, ignoreClarifyingParentheses)) {
      CommentTracker commentTracker = new CommentTracker();
      commentTracker.markUnchanged(body);
      PsiExpression newExpression = (PsiExpression)commentTracker.replaceAndRestoreComments(parenthesizedExpression, body);
      removeParentheses(newExpression, ignoreClarifyingParentheses);
    }
    else {
      removeParentheses(body, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromConditionalExpression(@NotNull PsiConditionalExpression conditionalExpression,
                                                            boolean ignoreClarifyingParentheses) {
    final PsiExpression condition = conditionalExpression.getCondition();
    removeParentheses(condition, ignoreClarifyingParentheses);
    final PsiExpression thenBranch = conditionalExpression.getThenExpression();
    if (thenBranch != null) {
      removeParentheses(thenBranch, ignoreClarifyingParentheses);
    }
    final PsiExpression elseBranch = conditionalExpression.getElseExpression();
    if (elseBranch != null) {
      removeParentheses(elseBranch, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromInstanceOfExpression(@NotNull PsiInstanceOfExpression instanceofExpression,
                                                           boolean ignoreClarifyingParentheses) {
    final PsiExpression operand = instanceofExpression.getOperand();
    removeParentheses(operand, ignoreClarifyingParentheses);
    final PsiPrimaryPattern pattern = instanceofExpression.getPattern();
    if (pattern != null) {
      removeParensFromPattern(pattern, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromPolyadicExpression(@NotNull PsiPolyadicExpression polyadicExpression,
                                                         boolean ignoreClarifyingParentheses) {
    for (PsiExpression operand : polyadicExpression.getOperands()) {
      if (!operand.isValid()) break;
      removeParentheses(operand, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromPostfixExpression(@NotNull PsiPostfixExpression postfixExpression,
                                                        boolean ignoreClarifyingParentheses) {
    final PsiExpression operand = postfixExpression.getOperand();
    removeParentheses(operand, ignoreClarifyingParentheses);
  }

  private static void removeParensFromPrefixExpression(@NotNull PsiPrefixExpression prefixExpression, boolean ignoreClarifyingParentheses) {
    final PsiExpression operand = prefixExpression.getOperand();
    if (operand != null) {
      removeParentheses(operand, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromArrayAccessExpression(@NotNull PsiArrayAccessExpression arrayAccessExpression,
                                                            boolean ignoreClarifyingParentheses) {
    final PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
    removeParentheses(arrayExpression, ignoreClarifyingParentheses);
    final PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
    if (indexExpression != null) {
      removeParentheses(indexExpression, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromTypeCastExpression(@NotNull PsiTypeCastExpression typeCastExpression,
                                                         boolean ignoreClarifyingParentheses) {
    final PsiExpression operand = typeCastExpression.getOperand();
    if (operand != null) {
      removeParentheses(operand, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromArrayInitializerExpression(@NotNull PsiArrayInitializerExpression arrayInitializerExpression,
                                                                 boolean ignoreClarifyingParentheses) {
    final PsiExpression[] initializers = arrayInitializerExpression.getInitializers();
    for (final PsiExpression initializer : initializers) {
      removeParentheses(initializer, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromAssignmentExpression(@NotNull PsiAssignmentExpression assignment,
                                                           boolean ignoreClarifyingParentheses) {
    final PsiExpression lhs = assignment.getLExpression();
    final PsiExpression rhs = assignment.getRExpression();
    removeParentheses(lhs, ignoreClarifyingParentheses);
    if (rhs != null) {
      removeParentheses(rhs, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromNewExpression(@NotNull PsiNewExpression newExpression, boolean ignoreClarifyingParentheses) {
    final PsiExpression[] dimensions = newExpression.getArrayDimensions();
    for (PsiExpression dimension : dimensions) {
      removeParentheses(dimension, ignoreClarifyingParentheses);
    }
    final PsiExpression qualifier = newExpression.getQualifier();
    if (qualifier != null) {
      removeParentheses(qualifier, ignoreClarifyingParentheses);
    }
    final PsiExpression arrayInitializer = newExpression.getArrayInitializer();
    if (arrayInitializer != null) {
      removeParentheses(arrayInitializer, ignoreClarifyingParentheses);
    }
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList != null) {
      final PsiExpression[] arguments = argumentList.getExpressions();
      for (PsiExpression argument : arguments) {
        removeParentheses(argument, ignoreClarifyingParentheses);
      }
    }
  }

  private static void removeParensFromMethodCallExpression(@NotNull PsiMethodCallExpression methodCallExpression,
                                                           boolean ignoreClarifyingParentheses) {
    final PsiReferenceExpression target = methodCallExpression.getMethodExpression();
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    removeParentheses(target, ignoreClarifyingParentheses);
    for (final PsiExpression argument : arguments) {
      removeParentheses(argument, ignoreClarifyingParentheses);
    }
  }
}
