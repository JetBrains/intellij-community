/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParenthesesUtils {

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
    if (getPrecedence(expression) >= precedence) {
      return '(' + expression.getText() + ')';
    }
    return expression.getText();
  }

  @Nullable public static PsiElement getParentSkipParentheses(PsiElement element) {
    PsiElement parent = element.getParent();
    while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiTypeCastExpression) {
      parent = parent.getParent();
    }
    return parent;
  }

  @Contract("null -> null")
  public static PsiExpression stripParentheses(@Nullable PsiExpression expression) {
    while (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      expression = parenthesizedExpression.getExpression();
    }
    return expression;
  }

  public static void removeParentheses(@NotNull PsiExpression expression, boolean ignoreClarifyingParentheses) {
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
      removeParensFromMethodCallExpression(methodCall, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      removeParensFromReferenceExpression(referenceExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      removeParensFromNewExpression(newExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      removeParensFromAssignmentExpression(assignmentExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiArrayInitializerExpression) {
      final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)expression;
      removeParensFromArrayInitializerExpression(arrayInitializerExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
      removeParensFromTypeCastExpression(typeCastExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)expression;
      removeParensFromArrayAccessExpression(arrayAccessExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      removeParensFromPrefixExpression(prefixExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)expression;
      removeParensFromPostfixExpression(postfixExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      removeParensFromPolyadicExpression(polyadicExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      final PsiInstanceOfExpression instanceofExpression = (PsiInstanceOfExpression)expression;
      removeParensFromInstanceOfExpression(instanceofExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      removeParensFromConditionalExpression(conditionalExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      removeParensFromParenthesizedExpression(parenthesizedExpression, ignoreClarifyingParentheses);
    }
    else if (expression instanceof PsiLambdaExpression) {
      final PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)expression;
      removeParensFromLambdaExpression(lambdaExpression, ignoreClarifyingParentheses);
    }
  }

  private static void removeParensFromLambdaExpression(PsiLambdaExpression lambdaExpression, boolean ignoreClarifyingParentheses) {
    final PsiElement body = lambdaExpression.getBody();
    if (body  instanceof PsiExpression) {
      removeParentheses((PsiExpression)body, ignoreClarifyingParentheses);
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
    if (body == null) {
      new CommentTracker().deleteAndRestoreComments(parenthesizedExpression);
      return;
    }
    final PsiElement parent = parenthesizedExpression.getParent();
    if (!(parent instanceof PsiExpression) || !areParenthesesNeeded(body, (PsiExpression)parent, ignoreClarifyingParentheses)) {
      PsiExpression newExpression = ExpressionUtils.replacePolyadicWithParent(parenthesizedExpression, body);
      if (newExpression == null){
        CommentTracker commentTracker = new CommentTracker();
        commentTracker.markUnchanged(body);
        newExpression = (PsiExpression)commentTracker.replaceAndRestoreComments(parenthesizedExpression, body);
      }
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
  }

  private static void removeParensFromPolyadicExpression(@NotNull PsiPolyadicExpression polyadicExpression,
                                                         boolean ignoreClarifyingParentheses) {
    for (PsiExpression operand : polyadicExpression.getOperands()) {
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
