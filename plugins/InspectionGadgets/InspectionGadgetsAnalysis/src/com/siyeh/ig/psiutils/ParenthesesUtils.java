/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ParenthesesUtils {

  private ParenthesesUtils() {}

  public static final int PARENTHESIZED_PRECEDENCE = 0;
  public static final int LITERAL_PRECEDENCE = 0;
  public static final int METHOD_CALL_PRECEDENCE = 1;
  public static final int POSTFIX_PRECEDENCE = 2;
  public static final int PREFIX_PRECEDENCE = 3;
  public static final int TYPE_CAST_PRECEDENCE = 4;
  public static final int MULTIPLICATIVE_PRECEDENCE = 5;
  public static final int ADDITIVE_PRECEDENCE = 6;
  public static final int SHIFT_PRECEDENCE = 7;
  public static final int RELATIONAL_PRECEDENCE = 8;
  public static final int EQUALITY_PRECEDENCE = 9;
  public static final int BINARY_AND_PRECEDENCE = 10;
  public static final int BINARY_XOR_PRECEDENCE = 11;
  public static final int BINARY_OR_PRECEDENCE = 12;
  public static final int AND_PRECEDENCE = 13;
  public static final int OR_PRECEDENCE = 14;
  public static final int CONDITIONAL_PRECEDENCE = 15;
  public static final int ASSIGNMENT_PRECEDENCE = 16;
  public static final int NUM_PRECEDENCES = 17;

  private static final Map<IElementType, Integer> s_binaryOperatorPrecedence = new HashMap<IElementType, Integer>(NUM_PRECEDENCES);

  static {
    s_binaryOperatorPrecedence.put(JavaTokenType.PLUS, ADDITIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.MINUS, ADDITIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.ASTERISK, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.DIV, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.PERC, MULTIPLICATIVE_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.ANDAND, AND_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.OROR, OR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.AND, BINARY_AND_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.OR, BINARY_OR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.XOR, BINARY_XOR_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.LTLT, SHIFT_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.GTGT, SHIFT_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.GTGTGT, SHIFT_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.GT, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.GE, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.LT, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.LE, RELATIONAL_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.EQEQ, EQUALITY_PRECEDENCE);
    s_binaryOperatorPrecedence.put(JavaTokenType.NE, EQUALITY_PRECEDENCE);
  }

  @Nullable public static PsiElement getParentSkipParentheses(PsiElement element) {
    PsiElement parent = element.getParent();
    while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiTypeCastExpression) {
      parent = parent.getParent();
    }
    return parent;
  }

  @Nullable
  public static PsiExpression stripParentheses(@Nullable PsiExpression expression) {
    while (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      expression = parenthesizedExpression.getExpression();
    }
    return expression;
  }

  public static boolean isCommutativeOperator(@NotNull IElementType token) {
    return !(token.equals(JavaTokenType.MINUS) ||
             token.equals(JavaTokenType.DIV) ||
             token.equals(JavaTokenType.PERC) ||
             token.equals(JavaTokenType.LTLT) ||
             token.equals(JavaTokenType.GTGT) ||
             token.equals(JavaTokenType.GTGTGT));
  }

  public static boolean isAssociativeOperation(PsiPolyadicExpression expression) {
    final IElementType tokenType = expression.getOperationTokenType();
    final PsiType type = expression.getType();
    final PsiPrimitiveType primitiveType;
    if (type instanceof PsiClassType) {
      primitiveType = PsiPrimitiveType.getUnboxedType(type);
      if (primitiveType == null) {
        return false;
      }
    } else if (type instanceof PsiPrimitiveType) {
      primitiveType = (PsiPrimitiveType)type;
    } else {
      return false;
    }
    if (JavaTokenType.PLUS == tokenType || JavaTokenType.ASTERISK == tokenType) {
      return primitiveType != PsiType.FLOAT && primitiveType != PsiType.DOUBLE;
    } else if (JavaTokenType.EQEQ == tokenType || JavaTokenType.NE == tokenType) {
      return primitiveType == PsiType.BOOLEAN;
    } else if (JavaTokenType.AND == tokenType || JavaTokenType.OR == tokenType || JavaTokenType.XOR == tokenType) {
      return true;
    } else if (JavaTokenType.OROR == tokenType || JavaTokenType.ANDAND == tokenType) {
      return true;
    }
    return false;
  }

  public static int getPrecedence(PsiExpression expression) {
    if (expression instanceof PsiThisExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiSuperExpression ||
        expression instanceof PsiClassObjectAccessExpression ||
        expression instanceof PsiArrayAccessExpression ||
        expression instanceof PsiArrayInitializerExpression) {
      return LITERAL_PRECEDENCE;
    }
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      if (referenceExpression.getQualifier() != null) {
        return METHOD_CALL_PRECEDENCE;
      }
      else {
        return LITERAL_PRECEDENCE;
      }
    }
    if (expression instanceof PsiMethodCallExpression || expression instanceof PsiNewExpression) {
      return METHOD_CALL_PRECEDENCE;
    }
    if (expression instanceof PsiTypeCastExpression) {
      return TYPE_CAST_PRECEDENCE;
    }
    if (expression instanceof PsiPrefixExpression) {
      return PREFIX_PRECEDENCE;
    }
    if (expression instanceof PsiPostfixExpression) {
      return POSTFIX_PRECEDENCE;
    }
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      return getPrecedenceForOperator(polyadicExpression.getOperationTokenType());
    }
    if (expression instanceof PsiInstanceOfExpression) {
      return RELATIONAL_PRECEDENCE;
    }
    if (expression instanceof PsiConditionalExpression) {
      return CONDITIONAL_PRECEDENCE;
    }
    if (expression instanceof PsiAssignmentExpression) {
      return ASSIGNMENT_PRECEDENCE;
    }
    if (expression instanceof PsiParenthesizedExpression) {
      return PARENTHESIZED_PRECEDENCE;
    }
    return -1;
  }

  public static int getPrecedenceForOperator(@NotNull IElementType operator) {
    final Integer precedence = s_binaryOperatorPrecedence.get(operator);
    if (precedence == null) {
      throw new IllegalArgumentException("unknown operator: " + operator);
    }
    return precedence.intValue();
  }

  public static void removeParentheses(@NotNull PsiExpression expression, boolean ignoreClarifyingParentheses) {
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
      removeParensFromMethodCallExpression(methodCall, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      removeParensFromReferenceExpression(referenceExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      removeParensFromNewExpression(newExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      removeParensFromAssignmentExpression(assignmentExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiArrayInitializerExpression) {
      final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)expression;
      removeParensFromArrayInitializerExpression(arrayInitializerExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
      removeParensFromTypeCastExpression(typeCastExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)expression;
      removeParensFromArrayAccessExpression(arrayAccessExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      removeParensFromPrefixExpression(prefixExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)expression;
      removeParensFromPostfixExpression(postfixExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      removeParensFromPolyadicExpression(polyadicExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiInstanceOfExpression) {
      final PsiInstanceOfExpression instanceofExpression = (PsiInstanceOfExpression)expression;
      removeParensFromInstanceOfExpression(instanceofExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      removeParensFromConditionalExpression(conditionalExpression, ignoreClarifyingParentheses);
    }
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      removeParensFromParenthesizedExpression(parenthesizedExpression, ignoreClarifyingParentheses);
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
      parenthesizedExpression.delete();
      return;
    }
    final PsiElement parent = parenthesizedExpression.getParent();
    if (!(parent instanceof PsiExpression) || parent instanceof PsiParenthesizedExpression ||
        parent instanceof PsiArrayInitializerExpression) {
      final PsiExpression newExpression = (PsiExpression)parenthesizedExpression.replace(body);
      removeParentheses(newExpression, ignoreClarifyingParentheses);
      return;
    }
    else if (parent instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)parent;
      if (parenthesizedExpression == arrayAccessExpression.getIndexExpression()) {
        // use addAfter() + delete() instead of replace() to
        // workaround automatic insertion of parentheses by psi
        final PsiExpression newExpression = (PsiExpression)parent.addAfter(body, parenthesizedExpression);
        parenthesizedExpression.delete();
        removeParentheses(newExpression, ignoreClarifyingParentheses);
        return;
      }
    }
    final PsiExpression parentExpression = (PsiExpression)parent;
    final int parentPrecedence = getPrecedence(parentExpression);
    final int childPrecedence = getPrecedence(body);
    if (parentPrecedence < childPrecedence) {
      final PsiElement bodyParent = body.getParent();
      final PsiParenthesizedExpression newParenthesizedExpression = (PsiParenthesizedExpression)parenthesizedExpression.replace(bodyParent);
      final PsiExpression expression = newParenthesizedExpression.getExpression();
      if (expression != null) {
        removeParentheses(expression, ignoreClarifyingParentheses);
      }
    }
    else if (parentPrecedence == childPrecedence) {
      if (parentExpression instanceof PsiPolyadicExpression && body instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression parentPolyadicExpression = (PsiPolyadicExpression)parentExpression;
        final IElementType parentOperator = parentPolyadicExpression.getOperationTokenType();
        final PsiPolyadicExpression bodyPolyadicExpression = (PsiPolyadicExpression)body;
        final IElementType bodyOperator = bodyPolyadicExpression.getOperationTokenType();
        final PsiType parentType = parentPolyadicExpression.getType();
        final PsiType bodyType = body.getType();
        if (parentType != null && parentType.equals(bodyType) && parentOperator.equals(bodyOperator)) {
          final PsiExpression[] parentOperands = parentPolyadicExpression.getOperands();
          if (PsiTreeUtil.isAncestor(parentOperands[0], body, true) || isCommutativeOperator(bodyOperator)) {
            // use addAfter() + delete() instead of replace() to
            // workaround automatic insertion of parentheses by psi
            final PsiExpression newExpression = (PsiExpression)parent.addAfter(body, parenthesizedExpression);
            parenthesizedExpression.delete();
            removeParentheses(newExpression, ignoreClarifyingParentheses);
            return;
          }
        }
        if (ignoreClarifyingParentheses) {
          if (parentOperator.equals(bodyOperator)) {
            removeParentheses(body, ignoreClarifyingParentheses);
          }
        }
        else {
          final PsiExpression newExpression = (PsiExpression)parenthesizedExpression.replace(body);
          removeParentheses(newExpression, ignoreClarifyingParentheses);
        }
      }
      else {
        final PsiExpression newExpression = (PsiExpression)parenthesizedExpression.replace(body);
        removeParentheses(newExpression, ignoreClarifyingParentheses);
      }
    }
    else {
      if (ignoreClarifyingParentheses && parent instanceof PsiPolyadicExpression &&
          (body instanceof PsiPolyadicExpression || body instanceof PsiInstanceOfExpression)) {
        removeParentheses(body, ignoreClarifyingParentheses);
      }
      else {
        final PsiExpression newExpression = (PsiExpression)parenthesizedExpression.replace(body);
        removeParentheses(newExpression, ignoreClarifyingParentheses);
      }
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

  public static boolean areParenthesesNeeded(PsiParenthesizedExpression expression, boolean ignoreClarifyingParentheses) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiExpression)) {
      return false;
    }
    final PsiExpression child = expression.getExpression();
    if (child == null) {
      return true;
    }
    if (parent instanceof PsiArrayAccessExpression) {
      final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)parent;
      final PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
      if (expression == indexExpression) {
        return false;
      }
    }
    return areParenthesesNeeded(child, (PsiExpression)parent, ignoreClarifyingParentheses);
  }

  public static boolean areParenthesesNeeded(PsiExpression expression, PsiExpression parentExpression,
                                             boolean ignoreClarifyingParentheses) {
    if (parentExpression instanceof PsiParenthesizedExpression || parentExpression instanceof PsiArrayInitializerExpression) {
      return false;
    }
    final int parentPrecedence = getPrecedence(parentExpression);
    final int childPrecedence = getPrecedence(expression);
    if (parentPrecedence > childPrecedence) {
      if (ignoreClarifyingParentheses) {
        if (expression instanceof PsiPolyadicExpression) {
          if (parentExpression instanceof PsiPolyadicExpression ||
              parentExpression instanceof PsiConditionalExpression ||
              parentExpression instanceof PsiInstanceOfExpression) {
            return true;
          }
        }
        else if (expression instanceof PsiInstanceOfExpression) {
          return true;
        }
      }
      return false;
    }
    if (parentExpression instanceof PsiPolyadicExpression && expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression parentPolyadicExpression = (PsiPolyadicExpression)parentExpression;
      final PsiType parentType = parentPolyadicExpression.getType();
      if (parentType == null) {
        return true;
      }
      final PsiPolyadicExpression childPolyadicExpression = (PsiPolyadicExpression)expression;
      final PsiType childType = childPolyadicExpression.getType();
      if (!parentType.equals(childType)) {
        return true;
      }
      if (childType.equalsToText(CommonClassNames.JAVA_LANG_STRING) &&
          !PsiTreeUtil.isAncestor(parentPolyadicExpression.getOperands()[0], childPolyadicExpression, true)) {
        final PsiExpression[] operands = childPolyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (!childType.equals(operand.getType())) {
            return true;
          }
        }
      }
      else if (childType.equals(PsiType.BOOLEAN)) {
        final PsiExpression[] operands = childPolyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (!PsiType.BOOLEAN.equals(operand.getType())) {
            return true;
          }
        }
      }
      final IElementType parentOperator = parentPolyadicExpression.getOperationTokenType();
      final IElementType childOperator = childPolyadicExpression.getOperationTokenType();
      if (ignoreClarifyingParentheses) {
        if (!childOperator.equals(parentOperator)) {
          return true;
        }
      }
      final PsiExpression[] parentOperands = parentPolyadicExpression.getOperands();
      if (!PsiTreeUtil.isAncestor(parentOperands[0], expression, false)) {
        if (!isAssociativeOperation(parentPolyadicExpression) ||
            JavaTokenType.DIV == childOperator || JavaTokenType.PERC == childOperator) {
          return true;
        }
      }
    }
    else if (parentExpression instanceof PsiConditionalExpression && expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)parentExpression;
      final PsiExpression condition = conditionalExpression.getCondition();
      return PsiTreeUtil.isAncestor(condition, expression, true);
    }
    return parentPrecedence < childPrecedence;
  }
}
