/*
 * Copyright 2005-2017 Bas Leijdekkers
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.HardcodedMethodConstants;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class ExpressionUtils {
  @NonNls static final Set<String> convertableBoxedClassNames = new HashSet<>(3);
  static {
    convertableBoxedClassNames.add(CommonClassNames.JAVA_LANG_BYTE);
    convertableBoxedClassNames.add(CommonClassNames.JAVA_LANG_CHARACTER);
    convertableBoxedClassNames.add(CommonClassNames.JAVA_LANG_SHORT);
  }

  private ExpressionUtils() {}

  @Nullable
  public static Object computeConstantExpression(@Nullable PsiExpression expression) {
    return computeConstantExpression(expression, false);
  }

  @Nullable
  public static Object computeConstantExpression(@Nullable PsiExpression expression, boolean throwConstantEvaluationOverflowException) {
    if (expression == null) {
      return null;
    }
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiConstantEvaluationHelper constantEvaluationHelper = psiFacade.getConstantEvaluationHelper();
    return constantEvaluationHelper.computeConstantExpression(expression, throwConstantEvaluationOverflowException);
  }

  public static boolean isConstant(PsiField field) {
    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
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

  @Contract("null -> false")
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

  @Contract("null -> false")
  public static boolean isNullLiteral(@Nullable PsiExpression expression) {
    expression = PsiUtil.deparenthesizeExpression(expression);
    return expression != null && PsiType.NULL.equals(expression.getType());
  }

  /**
   * Returns stream of sub-expressions of supplied expression which could be equal (by ==) to resulting
   * value of the expression. The expressions in returned stream are guaranteed not to be each other ancestors.
   * Also the expression value is guaranteed to be equal to one of returned sub-expressions.
   *
   * <p>
   * E.g. for {@code ((a) ? (Foo)b : (c))} the stream will contain b and c.
   * </p>
   *
   * @param expression expression to create a stream from
   * @return a new stream
   */
  public static Stream<PsiExpression> nonStructuralChildren(@NotNull PsiExpression expression) {
    return StreamEx.ofTree(expression, e -> {
      if (e instanceof PsiConditionalExpression) {
        PsiConditionalExpression ternary = (PsiConditionalExpression)e;
        return StreamEx.of(ternary.getThenExpression(), ternary.getElseExpression()).nonNull();
      }
      if (e instanceof PsiParenthesizedExpression) {
        return StreamEx.ofNullable(((PsiParenthesizedExpression)e).getExpression());
      }
      return null;
    }).remove(e -> e instanceof PsiConditionalExpression ||
                   e instanceof PsiParenthesizedExpression)
      .map(e -> {
        if(e instanceof PsiTypeCastExpression) {
          PsiExpression operand = ((PsiTypeCastExpression)e).getOperand();
          if(operand != null && !(e.getType() instanceof PsiPrimitiveType) &&
             (!(operand.getType() instanceof PsiPrimitiveType) || PsiType.NULL.equals(operand.getType()))) {
            // Ignore to-primitive/from-primitive casts as they may actually change the value
            return PsiUtil.skipParenthesizedExprDown(operand);
          }
        }
        return e;
      });
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
    if (VariableAccessUtils.variableIsUsed(variable, arrayExpression)) {
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
      boolean expressionSeen = false;
      for (int i = 0, length = operands.length; i < length; i++) {
        final PsiExpression operand = operands[i];
        if (PsiTreeUtil.isAncestor(operand, expression, false)) {
          if (i > 0) return true;
          expressionSeen = true;
        }
        else if ((!expressionSeen || i == 1) && TypeUtils.isJavaLangString(operand.getType())) {
          return false;
        }
      }
      return true;
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

  @Contract("null, _ -> null")
  @Nullable
  public static PsiVariable getVariableFromNullComparison(PsiExpression expression, boolean equals) {
    final PsiReferenceExpression referenceExpression = getReferenceExpressionFromNullComparison(expression, equals);
    final PsiElement target = referenceExpression != null ? referenceExpression.resolve() : null;
    return target instanceof PsiVariable ? (PsiVariable)target : null;
  }

  @Contract("null, _ -> null")
  @Nullable
  public static PsiReferenceExpression getReferenceExpressionFromNullComparison(PsiExpression expression, boolean equals) {
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
    PsiExpression comparedToNull = null;
    if (PsiType.NULL.equals(operands[0].getType())) {
      comparedToNull = operands[1];
    }
    else if (PsiType.NULL.equals(operands[1].getType())) {
      comparedToNull = operands[0];
    }
    comparedToNull = ParenthesesUtils.stripParentheses(comparedToNull);

    return comparedToNull instanceof PsiReferenceExpression ? (PsiReferenceExpression)comparedToNull : null;
  }

  /**
   * Returns the expression compared with null if the supplied {@link PsiBinaryExpression} is null check (either with {@code ==}
   * or with {@code !=}). Returns null otherwise.
   *
   * @param binOp binary expression to extract the value compared with null from
   * @return value compared with null
   */
  @Nullable
  public static PsiExpression getValueComparedWithNull(@NotNull PsiBinaryExpression binOp) {
    final IElementType tokenType = binOp.getOperationTokenType();
    if(!tokenType.equals(JavaTokenType.EQEQ) && !tokenType.equals(JavaTokenType.NE)) return null;
    final PsiExpression left = binOp.getLOperand();
    final PsiExpression right = binOp.getROperand();
    if(isNullLiteral(right)) return left;
    if(isNullLiteral(left)) return right;
    return null;
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

  /**
   * Returns true if the expression can be moved to earlier point in program order without possible semantic change or
   * notable performance handicap. Examples of simple expressions are:
   * - literal (number, char, string, class literal, true, false, null)
   * - compile-time constant
   * - this
   * - variable/parameter read
   * - static field read
   * - instance field read having 'this' as qualifier
   *
   * @param expression an expression to test (must be valid expression)
   * @return true if the supplied expression is simple
   */
  @Contract("null -> false")
  public static boolean isSimpleExpression(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiLiteralExpression ||
        expression instanceof PsiThisExpression ||
        expression instanceof PsiClassObjectAccessExpression ||
        isEvaluatedAtCompileTime(expression)) {
      return true;
    }
    if(expression instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression)expression).getQualifierExpression();
      if(qualifier == null || qualifier instanceof PsiThisExpression) return true;
      if(qualifier instanceof PsiReferenceExpression) {
        PsiElement resolvedQualifier = ((PsiReferenceExpression)qualifier).resolve();
        if(resolvedQualifier instanceof PsiClass) return true;
      }
    }
    return false;
  }

  /**
   * Returns assignment expression if supplied element is a statement which contains assignment expression
   * or it's an assignment expression itself. Only simple assignments are returned (like a = b, not a+= b).
   *
   * @param element element to get assignment expression from
   * @return extracted assignment or null if assignment is not found or assignment is compound
   */
  @Contract("null -> null")
  @Nullable
  public static PsiAssignmentExpression getAssignment(PsiElement element) {
    if(element instanceof PsiExpressionStatement) {
      element = ((PsiExpressionStatement)element).getExpression();
    }
    if (element instanceof PsiExpression) {
      element = PsiUtil.skipParenthesizedExprDown((PsiExpression)element);
      if (element instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression assignment = (PsiAssignmentExpression)element;
        if(assignment.getOperationTokenType().equals(JavaTokenType.EQ)) {
          return assignment;
        }
      }
    }
    return null;
  }

  /**
   * Returns an expression assigned to the target variable if supplied element is
   * either simple (non-compound) assignment expression or an expression statement containing assignment expression
   * and the corresponding assignment l-value is the reference to target variable.
   *
   * @param element element to get assignment expression from
   * @param target a variable to extract an assignment to
   * @return extracted assignment r-value or null if assignment is not found or assignment is compound or it's an assignment
   * to the wrong variable
   */
  @Contract("null, _ -> null; _, null -> null")
  public static PsiExpression getAssignmentTo(PsiElement element, PsiVariable target) {
    PsiAssignmentExpression assignment = getAssignment(element);
    if(assignment != null && isReferenceTo(assignment.getLExpression(), target)) {
      return assignment.getRExpression();
    }
    return null;
  }

  @Contract("null, _ -> false")
  public static boolean isLiteral(PsiElement element, Object value) {
    return element instanceof PsiLiteralExpression && value.equals(((PsiLiteralExpression)element).getValue());
  }

  public static boolean isAutoBoxed(@NotNull PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiParenthesizedExpression) {
      return false;
    }
    if (parent instanceof PsiExpressionList) {
      final PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method != null &&
            AnnotationUtil.isAnnotated(method, Collections.singletonList("java.lang.invoke.MethodHandle.PolymorphicSignature"))) {
          return false;
        }
      }
    }
    final PsiType expressionType = expression.getType();
    if (PsiPrimitiveType.getUnboxedType(expressionType) != null &&
        (parent instanceof PsiPrefixExpression || parent instanceof PsiPostfixExpression)) {
      return true;
    }
    if (expressionType == null || expressionType.equals(PsiType.VOID) || !TypeConversionUtil.isPrimitiveAndNotNull(expressionType)) {
      return false;
    }
    final PsiPrimitiveType primitiveType = (PsiPrimitiveType)expressionType;
    final PsiClassType boxedType = primitiveType.getBoxedType(expression);
    if (boxedType == null) {
      return false;
    }
    final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
    if (expectedType == null || ClassUtils.isPrimitive(expectedType)) {
      return false;
    }
    if (!expectedType.isAssignableFrom(boxedType)) {
      // JLS 5.2 Assignment Conversion
      // check if a narrowing primitive conversion is applicable
      if (!(expectedType instanceof PsiClassType) || !PsiUtil.isConstantExpression(expression)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)expectedType;
      final String className = classType.getCanonicalText();
      if (!convertableBoxedClassNames.contains(className)) {
        return false;
      }
      if (!PsiType.BYTE.equals(expressionType) && !PsiType.CHAR.equals(expressionType) &&
          !PsiType.SHORT.equals(expressionType) && !PsiType.INT.equals(expressionType)) {
        return false;
      }
    }
    return true;
  }

  /**
   * If any operand of supplied binary expression refers to the supplied variable, returns other operand;
   * otherwise returns null.
   *
   * @param binOp {@link PsiBinaryExpression} to extract the operand from
   * @param variable variable to check against
   * @return operand or null
   */
  @Contract("null, _ -> null; !null, null -> null")
  public static PsiExpression getOtherOperand(@Nullable PsiBinaryExpression binOp, @Nullable PsiVariable variable) {
    if(binOp == null || variable == null) return null;
    if(isReferenceTo(binOp.getLOperand(), variable)) return binOp.getROperand();
    if(isReferenceTo(binOp.getROperand(), variable)) return binOp.getLOperand();
    return null;
  }

  @Contract("null, _ -> false; _, null -> false")
  public static boolean isReferenceTo(PsiExpression expression, PsiVariable variable) {
    if (variable == null) return false;
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    return expression instanceof PsiReferenceExpression && ((PsiReferenceExpression)expression).isReferenceTo(variable);
  }

  /**
   * Returns a method call expression for the supplied qualifier
   *
   * @param qualifier for method call
   * @return a method call expression or null if the supplied expression is not a method call qualifier
   */
  @Contract(value = "null -> null", pure = true)
  public static PsiMethodCallExpression getCallForQualifier(PsiExpression qualifier) {
    if(qualifier == null) return null;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(qualifier.getParent());
    if(parent instanceof PsiReferenceExpression) {
      PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
      if(PsiTreeUtil.isAncestor(methodExpression.getQualifierExpression(), qualifier, false)) {
        PsiElement gParent = methodExpression.getParent();
        if (gParent instanceof PsiMethodCallExpression) {
          return (PsiMethodCallExpression)gParent;
        }
      }
    }
    return null;
  }

  /**
   * Returns an array expression from array length retrieval expression
   * @param expression expression to extract an array expression from
   * @return an array expression or null if supplied expression is not array length retrieval
   */
  @Nullable
  public static PsiExpression getArrayFromLengthExpression(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiReferenceExpression)) return null;
    final PsiReferenceExpression reference =
      (PsiReferenceExpression)expression;
    final String referenceName = reference.getReferenceName();
    if (!HardcodedMethodConstants.LENGTH.equals(referenceName)) return null;
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier == null) return null;
    final PsiType type = qualifier.getType();
    if (type == null || type.getArrayDimensions() <= 0) return null;
    return qualifier;
  }

  /**
   * Returns a qualifier for reference or creates a corresponding {@link PsiThisExpression} statement if
   * a qualifier is null
   *
   * @param ref a reference expression to get a qualifier from
   * @return a qualifier or created (non-physical) {@link PsiThisExpression}.
   */
  @NotNull
  public static PsiExpression getQualifierOrThis(@NotNull PsiReferenceExpression ref) {
    PsiExpression qualifier = ref.getQualifierExpression();
    if (qualifier != null) return qualifier;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(ref.getProject());
    PsiMember member = ObjectUtils.tryCast(ref.resolve(), PsiMember.class);
    if (member != null) {
      PsiClass memberClass = member.getContainingClass();
      if (memberClass != null) {
        PsiClass containingClass = ClassUtils.getContainingClass(ref);
        if (containingClass == null) {
          containingClass = PsiTreeUtil.getContextOfType(ref, PsiClass.class);
        }
        if (!InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
          containingClass = ClassUtils.getContainingClass(containingClass);
          while (containingClass != null && !InheritanceUtil.isInheritorOrSelf(containingClass, memberClass, true)) {
            containingClass = ClassUtils.getContainingClass(containingClass);
          }
          if (containingClass != null) {
            return factory.createExpressionFromText(containingClass.getQualifiedName() + "." + PsiKeyword.THIS, ref);
          }
        }
      }
    }
    return factory.createExpressionFromText(PsiKeyword.THIS, ref);
  }

  /**
   * Bind a reference element to a new name. The qualifier and type arguments (if present) remain the same
   *
   * @param ref reference element to rename
   * @param newName new name
   */
  public static void bindReferenceTo(@NotNull PsiReferenceExpression ref, @NotNull String newName) {
    PsiElement nameElement = ref.getReferenceNameElement();
    if(nameElement == null) {
      throw new IllegalStateException("Name element is null: "+ref);
    }
    if(newName.equals(nameElement.getText())) return;
    PsiIdentifier identifier = JavaPsiFacade.getElementFactory(ref.getProject()).createIdentifier(newName);
    nameElement.replace(identifier);
  }

  /**
   * Bind method call to a new name. Everything else like qualifier, type arguments or call arguments remain the same.
   *
   * @param call to rename
   * @param newName new name
   */
  public static void bindCallTo(@NotNull PsiMethodCallExpression call, @NotNull String newName) {
    bindReferenceTo(call.getMethodExpression(), newName);
  }

  /**
   * Returns the expression itself (probably with stripped parentheses) or the corresponding value if the expression is a local variable
   * reference which is initialized and not used anywhere else
   *
   * @param expression
   * @return a resolved expression or expression itself
   */
  @Contract("null -> null")
  @Nullable
  public static PsiExpression resolveExpression(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression reference = (PsiReferenceExpression)expression;
      PsiLocalVariable variable = ObjectUtils.tryCast(reference.resolve(), PsiLocalVariable.class);
      if (variable != null) {
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null && ReferencesSearch.search(variable).forEach(ref -> ref == reference)) {
          return initializer;
        }
      }
    }
    return expression;
  }
}