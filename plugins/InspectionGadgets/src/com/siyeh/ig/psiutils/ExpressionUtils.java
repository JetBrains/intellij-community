/*
 * Copyright 2005-2010 Bas Leijdekkers
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
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExpressionUtils {

    private ExpressionUtils() {}

    public static Object computeConstantExpression(
            @Nullable PsiExpression expression) {
        return computeConstantExpression(expression, false);
    }

    public static Object computeConstantExpression(
            @Nullable PsiExpression expression,
            boolean throwExceptionOnOverflow) {
        if (expression == null) {
            return null;
        }
        final Project project = expression.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiConstantEvaluationHelper constantEvaluationHelper =
                psiFacade.getConstantEvaluationHelper();
        return constantEvaluationHelper.computeConstantExpression(expression,
                throwExceptionOnOverflow);
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

    public static boolean isEvaluatedAtCompileTime(
            @Nullable PsiExpression expression) {
        if (expression instanceof PsiLiteralExpression) {
            return true;
        }
        if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            return isEvaluatedAtCompileTime(lhs) &&
                    isEvaluatedAtCompileTime(rhs);
        }
        if (expression instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)expression;
            final PsiExpression operand = prefixExpression.getOperand();
            return isEvaluatedAtCompileTime(operand);
        }
        if (expression instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)expression;
            final PsiElement qualifier = referenceExpression.getQualifier();
            if (qualifier instanceof PsiThisExpression) {
                return false;
            }
            final PsiElement element = referenceExpression.resolve();
            if (element instanceof PsiField) {
                final PsiField field = (PsiField)element;
                final PsiExpression initializer = field.getInitializer();
                return field.hasModifierProperty(PsiModifier.FINAL) &&
                        isEvaluatedAtCompileTime(initializer);
            }
            if (element instanceof PsiVariable) {
                final PsiVariable variable = (PsiVariable)element;
                if (PsiTreeUtil.isAncestor(variable, expression, true)) {
                    return false;
                }
                final PsiExpression initializer = variable.getInitializer();
                return variable.hasModifierProperty(PsiModifier.FINAL) &&
                        isEvaluatedAtCompileTime(initializer);
            }
        }
        if (expression instanceof PsiParenthesizedExpression) {
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            final PsiExpression unparenthesizedExpression =
                    parenthesizedExpression.getExpression();
            return isEvaluatedAtCompileTime(unparenthesizedExpression);
        }
        if (expression instanceof PsiConditionalExpression) {
            final PsiConditionalExpression conditionalExpression =
                    (PsiConditionalExpression)expression;
            final PsiExpression condition = conditionalExpression.getCondition();
            final PsiExpression thenExpression =
                    conditionalExpression.getThenExpression();
            final PsiExpression elseExpression =
                    conditionalExpression.getElseExpression();
            return isEvaluatedAtCompileTime(condition) &&
                    isEvaluatedAtCompileTime(thenExpression) &&
                    isEvaluatedAtCompileTime(elseExpression);
        }
        if (expression instanceof PsiTypeCastExpression) {
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression)expression;
            final PsiTypeElement castType = typeCastExpression.getCastType();
            if (castType == null) {
                return false;
            }
            final PsiType type = castType.getType();
            return TypeUtils.typeEquals("java.lang.String", type);
        }
        return false;
    }

    public static boolean isEmptyStringLiteral(
            @Nullable PsiExpression expression) {
        if (!(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        final String text = expression.getText();
        return "\"\"".equals(text);
    }

    public static boolean isNullLiteral(@Nullable PsiExpression expression) {
        if (!(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        final String text = expression.getText();
        return PsiKeyword.NULL.equals(text);
    }

    public static boolean isZero(@Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final PsiType expressionType = expression.getType();
        final Object value = ConstantExpressionUtil.computeCastTo(expression,
                expressionType);
        if(value == null){
            return false;
        }
        if(value instanceof Double && ((Double) value).doubleValue() == 0.0) {
            return true;
        }
        if(value instanceof Float && ((Float) value).floatValue() == 0.0f) {
            return true;
        }
        if(value instanceof Integer && ((Integer) value).intValue() == 0){
            return true;
        }
        if(value instanceof Long && ((Long) value).longValue() == 0L){
            return true;
        }
        if(value instanceof Short && ((Short) value).shortValue() == 0){
            return true;
        }
        if(value instanceof Character && ((Character) value).charValue() == 0){
            return true;
        }
        return value instanceof Byte && ((Byte) value).byteValue() == 0;
    }

    public static boolean isOne(@Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final PsiType expressionType = expression.getType();
        final Object value = ConstantExpressionUtil.computeCastTo(
                expression, expressionType);
        if(value == null){
            return false;
        }
        //noinspection FloatingPointEquality
        if(value instanceof Double && ((Double) value).doubleValue() == 1.0) {
            return true;
        }
        if(value instanceof Float && ((Float) value).floatValue() == 1.0f) {
            return true;
        }
        if(value instanceof Integer && ((Integer) value).intValue() == 1){
            return true;
        }
        if(value instanceof Long && ((Long) value).longValue() == 1L){
            return true;
        }
        if(value instanceof Short && ((Short) value).shortValue() == 1){
            return true;
        }
        if(value instanceof Character && ((Character) value).charValue() == 1){
            return true;
        }
        return value instanceof Byte && ((Byte) value).byteValue() == 1;
    }

    public static boolean isNegation(@Nullable PsiExpression condition,
                                     boolean ignoreNegatedNullComparison) {
        condition = ParenthesesUtils.stripParentheses(condition);
        if (condition instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)condition;
            final PsiJavaToken sign = prefixExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            return tokenType.equals(JavaTokenType.EXCL);
        } else if (condition instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)condition;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return false;
            }
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.NE)) {
                if (ignoreNegatedNullComparison) {
                    final String lhsText = lhs.getText();
                    final String rhsText = rhs.getText();
                    return !PsiKeyword.NULL.equals(lhsText) &&
                            !PsiKeyword.NULL.equals(rhsText);
                } else {
                    return true;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
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
        final PsiExpression strippedExpression =
                ParenthesesUtils.stripParentheses(expression);
        if (VariableAccessUtils.evaluatesToVariable(strippedExpression,
                variable)) {
            return true;
        }
        if (!(strippedExpression instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)strippedExpression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
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

    public static boolean isComparison(@Nullable PsiExpression expression,
                                       @NotNull PsiLocalVariable variable) {
        expression =
                ParenthesesUtils.stripParentheses(expression);
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (tokenType.equals(JavaTokenType.LT)) {
            PsiExpression lhs = binaryExpression.getLOperand();
            lhs = ParenthesesUtils.stripParentheses(lhs);
            return VariableAccessUtils.evaluatesToVariable(lhs, variable);
        } else if (tokenType.equals(JavaTokenType.GT)) {
            PsiExpression rhs = binaryExpression.getROperand();
            rhs = ParenthesesUtils.stripParentheses(rhs);
            return VariableAccessUtils.evaluatesToVariable(rhs, variable);
        }
        return false;
    }

    public static boolean isZeroLengthArrayConstruction(
            @Nullable PsiExpression expression) {
        if (!(expression instanceof PsiNewExpression)) {
            return false;
        }
        final PsiNewExpression newExpression = (PsiNewExpression) expression;
        final PsiExpression[] dimensions = newExpression.getArrayDimensions();
        if(dimensions.length == 0){
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
}