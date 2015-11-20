/*
 * Copyright 2005-2015 Bas Leijdekkers
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

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExpressionUtils {

  private ExpressionUtils() {}

  @Nullable
  public static Object computeConstantExpression(
    @Nullable PsiExpression expression) {
    return computeConstantExpression(expression, false);
  }

  @Nullable
  public static Object computeConstantExpression(
    @Nullable PsiExpression expression,
    boolean throwConstantEvaluationOverflowException) {
    if (expression == null) {
      return null;
    }
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiConstantEvaluationHelper constantEvaluationHelper =
      psiFacade.getConstantEvaluationHelper();
    return constantEvaluationHelper.computeConstantExpression(expression,
                                                              throwConstantEvaluationOverflowException);
  }

  public static boolean isConstant(PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL) ||
        !field.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    if (CollectionUtils.isEmptyArray(field)) {
      return true;
    }
    final PsiType type = field.getType();
    return ClassUtils.isImmutable(type);
  }

  public static boolean hasExpressionCount(@Nullable PsiExpressionList expressionList, int count) {
    return ControlFlowUtils.hasChildrenOfTypeCount(expressionList, count, PsiExpression.class);
  }

  @Nullable
  public static PsiExpression getFirstExpressionInList(@Nullable PsiExpressionList expressionList) {
    return PsiTreeUtil.getChildOfType(expressionList, PsiExpression.class);
  }

  @Nullable
  public static PsiExpression getOnlyExpressionInList(@Nullable PsiExpressionList expressionList) {
    return ControlFlowUtils.getOnlyChildOfType(expressionList, PsiExpression.class);
  }

  public static boolean isDeclaredConstant(PsiExpression expression) {
    PsiField field =
      PsiTreeUtil.getParentOfType(expression, PsiField.class);
    if (field == null) {
      final PsiAssignmentExpression assignmentExpression =
        PsiTreeUtil.getParentOfType(expression,
                                    PsiAssignmentExpression.class);
      if (assignmentExpression == null) {
        return false;
      }
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return false;
      }
      field = (PsiField)target;
    }
    return field.hasModifierProperty(PsiModifier.STATIC) &&
           field.hasModifierProperty(PsiModifier.FINAL);
  }

  public static boolean isEvaluatedAtCompileTime(@Nullable PsiExpression expression) {
    if (expression instanceof PsiLiteralExpression) {
      return true;
    }
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (!isEvaluatedAtCompileTime(operand)) {
          return false;
        }
      }
      return true;
    }
    if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      final PsiExpression operand = prefixExpression.getOperand();
      return isEvaluatedAtCompileTime(operand);
    }
    if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement qualifier = referenceExpression.getQualifier();
      if (qualifier instanceof PsiThisExpression) {
        return false;
      }
      final PsiElement element = referenceExpression.resolve();
      if (element instanceof PsiField) {
        final PsiField field = (PsiField)element;
        final PsiExpression initializer = field.getInitializer();
        return field.hasModifierProperty(PsiModifier.FINAL) && isEvaluatedAtCompileTime(initializer);
      }
      if (element instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)element;
        if (PsiTreeUtil.isAncestor(variable, expression, true)) {
          return false;
        }
        final PsiExpression initializer = variable.getInitializer();
        return variable.hasModifierProperty(PsiModifier.FINAL) && isEvaluatedAtCompileTime(initializer);
      }
    }
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      final PsiExpression deparenthesizedExpression = parenthesizedExpression.getExpression();
      return isEvaluatedAtCompileTime(deparenthesizedExpression);
    }
    if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      final PsiExpression condition = conditionalExpression.getCondition();
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      final PsiExpression elseExpression = conditionalExpression.getElseExpression();
      return isEvaluatedAtCompileTime(condition) &&
             isEvaluatedAtCompileTime(thenExpression) &&
             isEvaluatedAtCompileTime(elseExpression);
    }
    if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
      final PsiTypeElement castType = typeCastExpression.getCastType();
      if (castType == null) {
        return false;
      }
      final PsiType type = castType.getType();
      return TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, type);
    }
    return false;
  }

  @Nullable
  public static String getLiteralString(@Nullable PsiExpression expression) {
    final PsiLiteralExpression literal = getLiteral(expression);
    if (literal == null) {
      return null;
    }
    final Object value = literal.getValue();
    if (value == null) {
      return null;
    }
    return value.toString();
  }

  @Nullable
  public static PsiLiteralExpression getLiteral(@Nullable PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression instanceof PsiLiteralExpression) {
      return (PsiLiteralExpression)expression;
    }
    if (!(expression instanceof PsiTypeCastExpression)) {
      return null;
    }
    final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)expression;
    final PsiExpression operand = ParenthesesUtils.stripParentheses(typeCastExpression.getOperand());
    if (!(operand instanceof PsiLiteralExpression)) {
      return null;
    }
    return (PsiLiteralExpression)operand;
  }

  public static boolean isLiteral(@Nullable PsiExpression expression) {
    return getLiteral(expression) != null;
  }

  public static boolean isEmptyStringLiteral(@Nullable PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiLiteralExpression)) {
      return false;
    }
    final String text = expression.getText();
    return "\"\"".equals(text);
  }

  public static boolean isNullLiteral(@Nullable PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    return expression != null && PsiType.NULL.equals(expression.getType());
  }

  public static boolean isZero(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final PsiType expressionType = expression.getType();
    final Object value = ConstantExpressionUtil.computeCastTo(expression,
                                                              expressionType);
    if (value == null) {
      return false;
    }
    if (value instanceof Double && ((Double)value).doubleValue() == 0.0) {
      return true;
    }
    if (value instanceof Float && ((Float)value).floatValue() == 0.0f) {
      return true;
    }
    if (value instanceof Integer && ((Integer)value).intValue() == 0) {
      return true;
    }
    if (value instanceof Long && ((Long)value).longValue() == 0L) {
      return true;
    }
    if (value instanceof Short && ((Short)value).shortValue() == 0) {
      return true;
    }
    if (value instanceof Character && ((Character)value).charValue() == 0) {
      return true;
    }
    return value instanceof Byte && ((Byte)value).byteValue() == 0;
  }

  public static boolean isOne(@Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    final Object value = computeConstantExpression(expression);
    if (value == null) {
      return false;
    }
    //noinspection FloatingPointEquality
    if (value instanceof Double && ((Double)value).doubleValue() == 1.0) {
      return true;
    }
    if (value instanceof Float && ((Float)value).floatValue() == 1.0f) {
      return true;
    }
    if (value instanceof Integer && ((Integer)value).intValue() == 1) {
      return true;
    }
    if (value instanceof Long && ((Long)value).longValue() == 1L) {
      return true;
    }
    if (value instanceof Short && ((Short)value).shortValue() == 1) {
      return true;
    }
    if (value instanceof Character && ((Character)value).charValue() == 1) {
      return true;
    }
    return value instanceof Byte && ((Byte)value).byteValue() == 1;
  }

  public static boolean isNegation(@Nullable PsiExpression condition,
                                   boolean ignoreNegatedNullComparison, boolean ignoreNegatedZeroComparison) {
    condition = ParenthesesUtils.stripParentheses(condition);
    if (condition instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)condition;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      return tokenType.equals(JavaTokenType.EXCL);
    }
    else if (condition instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
      if (lhs == null || rhs == null) {
        return false;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.NE)) {
        if (ignoreNegatedNullComparison) {
          final String lhsText = lhs.getText();
          final String rhsText = rhs.getText();
          if (PsiKeyword.NULL.equals(lhsText) || PsiKeyword.NULL.equals(rhsText)) {
            return false;
          }
        }
        return !(ignoreNegatedZeroComparison && (isZeroLiteral(lhs) || isZeroLiteral(rhs)));
      }
    }
    return false;
  }

  private static boolean isZeroLiteral(PsiExpression expression) {
    if (!(expression instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
    final Object value = literalExpression.getValue();
    if (value instanceof Integer) {
      if (((Integer)value).intValue() == 0) {
        return true;
      }
    } else if (value instanceof Long) {
      if (((Long)value).longValue() == 0L) {
        return true;
      }
    }
    return false;
  }

  public static boolean isOffsetArrayAccess(
    @Nullable PsiExpression expression, @NotNull PsiVariable variable) {
    final PsiExpression strippedExpression =
      ParenthesesUtils.stripParentheses(expression);
    if (!(strippedExpression instanceof PsiArrayAccessExpression)) {
      return false;
    }
    final PsiArrayAccessExpression arrayAccessExpression =
      (PsiArrayAccessExpression)strippedExpression;
    final PsiExpression arrayExpression =
      arrayAccessExpression.getArrayExpression();
    if (isOffsetArrayAccess(arrayExpression, variable)) {
      return false;
    }
    final PsiExpression index = arrayAccessExpression.getIndexExpression();
    if (index == null) {
      return false;
    }
    return expressionIsOffsetVariableLookup(index, variable);
  }

  private static boolean expressionIsOffsetVariableLookup(
    @Nullable PsiExpression expression, @NotNull PsiVariable variable) {
    if (VariableAccessUtils.evaluatesToVariable(expression,
                                                variable)) {
      return true;
    }
    final PsiExpression strippedExpression =
      ParenthesesUtils.stripParentheses(expression);
    if (!(strippedExpression instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExpression =
      (PsiBinaryExpression)strippedExpression;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (!JavaTokenType.PLUS.equals(tokenType) &&
        !JavaTokenType.MINUS.equals(tokenType)) {
      return false;
    }
    final PsiExpression lhs = binaryExpression.getLOperand();
    if (expressionIsOffsetVariableLookup(lhs, variable)) {
      return true;
    }
    final PsiExpression rhs = binaryExpression.getROperand();
    return expressionIsOffsetVariableLookup(rhs, variable) &&
           !JavaTokenType.MINUS.equals(tokenType);
  }

  public static boolean isVariableLessThanComparison(
    @Nullable PsiExpression expression,
    @NotNull PsiVariable variable) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExpression =
      (PsiBinaryExpression)expression;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.LT) ||
        tokenType.equals(JavaTokenType.LE)) {
      final PsiExpression lhs = binaryExpression.getLOperand();
      return VariableAccessUtils.evaluatesToVariable(lhs, variable);
    }
    else if (tokenType.equals(JavaTokenType.GT) ||
             tokenType.equals(JavaTokenType.GE)) {
      final PsiExpression rhs = binaryExpression.getROperand();
      return VariableAccessUtils.evaluatesToVariable(rhs, variable);
    }
    return false;
  }

  public static boolean isVariableGreaterThanComparison(
    @Nullable PsiExpression expression,
    @NotNull PsiVariable variable) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExpression =
      (PsiBinaryExpression)expression;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.GT) ||
        tokenType.equals(JavaTokenType.GE)) {
      final PsiExpression lhs = binaryExpression.getLOperand();
      return VariableAccessUtils.evaluatesToVariable(lhs, variable);
    }
    else if (tokenType.equals(JavaTokenType.LT) ||
             tokenType.equals(JavaTokenType.LE)) {
      final PsiExpression rhs = binaryExpression.getROperand();
      return VariableAccessUtils.evaluatesToVariable(rhs, variable);
    }
    return false;
  }

  public static boolean isZeroLengthArrayConstruction(
    @Nullable PsiExpression expression) {
    if (!(expression instanceof PsiNewExpression)) {
      return false;
    }
    final PsiNewExpression newExpression = (PsiNewExpression)expression;
    final PsiExpression[] dimensions = newExpression.getArrayDimensions();
    if (dimensions.length == 0) {
      final PsiArrayInitializerExpression arrayInitializer =
        newExpression.getArrayInitializer();
      if (arrayInitializer == null) {
        return false;
      }
      final PsiExpression[] initializers =
        arrayInitializer.getInitializers();
      return initializers.length == 0;
    }
    for (PsiExpression dimension : dimensions) {
      final String dimensionText = dimension.getText();
      if (!"0".equals(dimensionText)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isStringConcatenationOperand(PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression polyadicExpression =
      (PsiPolyadicExpression)parent;
    if (!JavaTokenType.PLUS.equals(
      polyadicExpression.getOperationTokenType())) {
      return false;
    }
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operands.length < 2) {
      return false;
    }
    final int index = ArrayUtil.indexOf(operands, expression);
    for (int i = 0; i < index; i++) {
      final PsiType type = operands[i].getType();
      if (TypeUtils.isJavaLangString(type)) {
        return true;
      }
    }
    if (index == 0) {
      final PsiType type = operands[index + 1].getType();
      return TypeUtils.isJavaLangString(type);
    }
    return false;
  }


  public static boolean isConstructorInvocation(PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression =
      (PsiMethodCallExpression)element;
    final PsiReferenceExpression methodExpression =
      methodCallExpression.getMethodExpression();
    final String callName = methodExpression.getReferenceName();
    return PsiKeyword.THIS.equals(callName) ||
           PsiKeyword.SUPER.equals(callName);
  }

  public static boolean hasType(@Nullable PsiExpression expression, @NonNls @NotNull String typeName) {
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    return TypeUtils.typeEquals(typeName, type);
  }

  public static boolean hasStringType(@Nullable PsiExpression expression) {
    return hasType(expression, CommonClassNames.JAVA_LANG_STRING);
  }

  public static boolean isConversionToStringNecessary(PsiExpression expression, boolean throwable) {
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
    if (parent instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
      final PsiType type = polyadicExpression.getType();
      if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, type)) {
        return true;
      }
      final PsiExpression[] operands = polyadicExpression.getOperands();
      int index = -1;
      for (int i = 0, length = operands.length; i < length; i++) {
        final PsiExpression operand = operands[i];
        if (PsiTreeUtil.isAncestor(operand, expression, false)) {
          index = i;
          break;
        }
      }
      if (index > 0) {
        if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, operands[index - 1].getType())) {
          return true;
        }
      } else if (operands.length > 1) {
        if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING, operands[index + 1].getType())) {
          return true;
        }
      } else {
        return true;
      }
    } else if (parent instanceof PsiExpressionList) {
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      final PsiElement grandParent = expressionList.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return true;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression1 = methodCallExpression.getMethodExpression();
      @NonNls final String name = methodExpression1.getReferenceName();
      final PsiExpression[] expressions = expressionList.getExpressions();
      if ("insert".equals(name)) {
        if (expressions.length < 2 || !expression.equals(ParenthesesUtils.stripParentheses(expressions[1]))) {
          return true;
        }
        if (!isCallToMethodIn(methodCallExpression, "java.lang.StringBuilder", "java.lang.StringBuffer")) {
          return true;
        }
      } else if ("append".equals(name)) {
        if (expressions.length < 1 || !expression.equals(ParenthesesUtils.stripParentheses(expressions[0]))) {
          return true;
        }
        if (!isCallToMethodIn(methodCallExpression, "java.lang.StringBuilder", "java.lang.StringBuffer")) {
          return true;
        }
      } else if ("print".equals(name) || "println".equals(name)) {
        if (!isCallToMethodIn(methodCallExpression, "java.io.PrintStream", "java.io.PrintWriter")) {
          return true;
        }
      } else if ("trace".equals(name) || "debug".equals(name) || "info".equals(name) || "warn".equals(name) || "error".equals(name)) {
        if (!isCallToMethodIn(methodCallExpression, "org.slf4j.Logger")) {
          return true;
        }
        int l = 1;
        for (int i = 0; i < expressions.length; i++) {
          final PsiExpression expression1 = expressions[i];
          if (i == 0 && TypeUtils.expressionHasTypeOrSubtype(expression1, "org.slf4j.Marker")) {
            l = 2;
          }
          if (expression1 == expression) {
            if (i < l || (throwable && i == expressions.length - 1)) {
              return true;
            }
          }
        }
      } else {
        return true;
      }
    } else {
      return true;
    }
    return false;
  }

  private static boolean isCallToMethodIn(PsiMethodCallExpression methodCallExpression, String... classNames) {
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String qualifiedName = containingClass.getQualifiedName();
    for (String className : classNames) {
      if (className.equals(qualifiedName)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isNegative(@NotNull PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiPrefixExpression)) {
      return false;
    }
    final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
    final IElementType tokenType = prefixExpression.getOperationTokenType();
    return JavaTokenType.MINUS.equals(tokenType);
  }

  @Nullable
  public static PsiVariable getVariableFromNullComparison(PsiExpression expression, boolean equals) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiPolyadicExpression)) {
      return null;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    if (equals) {
      if (!JavaTokenType.EQEQ.equals(tokenType)) {
        return null;
      }
    }
    else {
      if (!JavaTokenType.NE.equals(tokenType)) {
        return null;
      }
    }
    final PsiExpression[] operands = polyadicExpression.getOperands();
    if (operands.length != 2) {
      return null;
    }
    if (PsiType.NULL.equals(operands[0].getType())) {
      return getVariable(operands[1]);
    }
    else if (PsiType.NULL.equals(operands[1].getType())) {
      return getVariable(operands[0]);
    }
    return null;
  }

  public static PsiVariable getVariable(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiReferenceExpression)) {
      return null;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return null;
    }
    return (PsiVariable)target;
  }

  public static boolean isConcatenation(PsiElement element) {
    if (!(element instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
    final PsiType type = expression.getType();
    return type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }

  public static boolean isAnnotatedNotNull(PsiExpression expression) {
    return isAnnotated(expression, false);
  }

  public static boolean isAnnotatedNullable(PsiExpression expression) {
    return isAnnotated(expression, true);
  }

  private static boolean isAnnotated(PsiExpression expression, boolean nullable) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiModifierListOwner)) {
      return false;
    }
    final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)target;
    return nullable ?
           NullableNotNullManager.isNullable(modifierListOwner):
           NullableNotNullManager.isNotNull(modifierListOwner);
  }
}