/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ParenthesesUtils{

    private ParenthesesUtils(){}

    private static final int PARENTHESIZED_PRECEDENCE = 0;
    private static final int LITERAL_PRECEDENCE = 0;
    public static final int METHOD_CALL_PRECEDENCE = 1;
    private static final int POSTFIX_PRECEDENCE = 2;
    public static final int PREFIX_PRECEDENCE = 3;
    public static final int TYPE_CAST_PRECEDENCE = 4;
    public static final int MULTIPLICATIVE_PRECEDENCE = 5;
    private static final int ADDITIVE_PRECEDENCE = 6;
    public static final int SHIFT_PRECEDENCE = 7;
    private static final int RELATIONAL_PRECEDENCE = 8;
    private static final int EQUALITY_PRECEDENCE = 9;
    private static final int BINARY_AND_PRECEDENCE = 10;
    private static final int BINARY_XOR_PRECEDENCE = 11;
    private static final int BINARY_OR_PRECEDENCE = 12;
    public static final int AND_PRECEDENCE = 13;
    public static final int OR_PRECEDENCE = 14;
    public static final int CONDITIONAL_PRECEDENCE = 15;
    private static final int ASSIGNMENT_PRECEDENCE = 16;
    private static final int NUM_PRECEDENCES = 17;

    private static final Map<String, Integer> s_binaryOperatorPrecedence =
            new HashMap<String, Integer>(NUM_PRECEDENCES);

    static {
        s_binaryOperatorPrecedence.put("+", ADDITIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("-", ADDITIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("*", MULTIPLICATIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("/", MULTIPLICATIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("%", MULTIPLICATIVE_PRECEDENCE);
        s_binaryOperatorPrecedence.put("&&", AND_PRECEDENCE);
        s_binaryOperatorPrecedence.put("||", OR_PRECEDENCE);
        s_binaryOperatorPrecedence.put("&", BINARY_AND_PRECEDENCE);
        s_binaryOperatorPrecedence.put("|", BINARY_OR_PRECEDENCE);
        s_binaryOperatorPrecedence.put("^", BINARY_XOR_PRECEDENCE);
        s_binaryOperatorPrecedence.put("<<", SHIFT_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">>", SHIFT_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">>>", SHIFT_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">", RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put(">=", RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put("<", RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put("<=", RELATIONAL_PRECEDENCE);
        s_binaryOperatorPrecedence.put("==", EQUALITY_PRECEDENCE);
        s_binaryOperatorPrecedence.put("!=", EQUALITY_PRECEDENCE);
    }

    @Nullable
    public static PsiExpression stripParentheses(
            @Nullable PsiExpression expression){
        while(expression instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            expression = parenthesizedExpression.getExpression();
        }
        return expression;
    }

    public static boolean isCommutativeBinaryOperator(
            @NotNull IElementType token) {
        return !(token.equals(JavaTokenType.MINUS) ||
                token.equals(JavaTokenType.DIV) ||
                token.equals(JavaTokenType.PERC) ||
                token.equals(JavaTokenType.LTLT) ||
                token.equals(JavaTokenType.GTGT) ||
                token.equals(JavaTokenType.GTGTGT));
    }

    public static int getPrecedence(PsiExpression expression){
        if(expression instanceof PsiThisExpression ||
           expression instanceof PsiLiteralExpression ||
           expression instanceof PsiSuperExpression ||
           expression instanceof PsiClassObjectAccessExpression ||
           expression instanceof PsiArrayAccessExpression ||
           expression instanceof PsiArrayInitializerExpression){
            return LITERAL_PRECEDENCE;
        }
        if(expression instanceof PsiReferenceExpression){
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)expression;
            if(referenceExpression.getQualifier() != null){
                return METHOD_CALL_PRECEDENCE;
            } else{
                return LITERAL_PRECEDENCE;
            }
        }
        if(expression instanceof PsiMethodCallExpression ||
                expression instanceof PsiNewExpression){
            return METHOD_CALL_PRECEDENCE;
        }
        if(expression instanceof PsiTypeCastExpression){
            return TYPE_CAST_PRECEDENCE;
        }
        if(expression instanceof PsiPrefixExpression){
            return PREFIX_PRECEDENCE;
        }
        if(expression instanceof PsiPostfixExpression){
            return POSTFIX_PRECEDENCE;
        }
        if(expression instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiJavaToken sign =
                    binaryExpression.getOperationSign();
            return getPrecedenceForBinaryOperator(sign);
        }
        if(expression instanceof PsiInstanceOfExpression){
            return RELATIONAL_PRECEDENCE;
        }
        if(expression instanceof PsiConditionalExpression){
            return CONDITIONAL_PRECEDENCE;
        }
        if(expression instanceof PsiAssignmentExpression){
            return ASSIGNMENT_PRECEDENCE;
        }
        if(expression instanceof PsiParenthesizedExpression){
            return PARENTHESIZED_PRECEDENCE;
        }
        return -1;
    }

    private static int getPrecedenceForBinaryOperator(
            @NotNull PsiJavaToken sign){
        final String operator = sign.getText();
        final Integer precedence = s_binaryOperatorPrecedence.get(operator);
        return precedence.intValue();
    }

    public static void removeParentheses(@NotNull PsiExpression expression,
                                         boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        if(expression instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression methodCall =
                    (PsiMethodCallExpression)expression;
            removeParensFromMethodCallExpression(methodCall,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiReferenceExpression){
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)expression;
            removeParensFromReferenceExpression(referenceExpression,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiNewExpression){
            final PsiNewExpression newExpression = (PsiNewExpression)expression;
            removeParensFromNewExpression(newExpression,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiAssignmentExpression){
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression)expression;
            removeParensFromAssignmentExpression(assignmentExpression,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiArrayInitializerExpression){
            final PsiArrayInitializerExpression arrayInitializerExpression =
                    (PsiArrayInitializerExpression)expression;
            removeParensFromArrayInitializerExpression(
                    arrayInitializerExpression, ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiTypeCastExpression){
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression)expression;
            removeParensFromTypeCastExpression(typeCastExpression,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiArrayAccessExpression){
            final PsiArrayAccessExpression arrayAccessExpression =
                    (PsiArrayAccessExpression)expression;
            removeParensFromArrayAccessExpression(arrayAccessExpression,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiPrefixExpression){
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)expression;
            removeParensFromPrefixExpression(prefixExpression,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiPostfixExpression){
            final PsiPostfixExpression postfixExpression =
                    (PsiPostfixExpression)expression;
            removeParensFromPostfixExpression(postfixExpression,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            removeParensFromBinaryExpression(binaryExpression,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiInstanceOfExpression){
            final PsiInstanceOfExpression instanceofExpression =
                    (PsiInstanceOfExpression)expression;
            removeParensFromInstanceOfExpression(instanceofExpression,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiConditionalExpression){
            final PsiConditionalExpression conditionalExpression =
                    (PsiConditionalExpression)expression;
            removeParensFromConditionalExpression(conditionalExpression,
                    ignoreClarifyingParentheses);
        }
        if(expression instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            removeParensFromParenthesizedExpression(
                    parenthesizedExpression, ignoreClarifyingParentheses);
        }
    }

    private static void removeParensFromReferenceExpression(
            @NotNull PsiReferenceExpression referenceExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression qualifier =
                referenceExpression.getQualifierExpression();
        if(qualifier != null){
            removeParentheses(qualifier, ignoreClarifyingParentheses);
        }
    }

    private static void removeParensFromParenthesizedExpression(
            @NotNull PsiParenthesizedExpression parenthesizedExpression, 
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        PsiExpression body = parenthesizedExpression.getExpression();
        while(body instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression innerParenthesizedExpression =
                    (PsiParenthesizedExpression)body;
            body = innerParenthesizedExpression.getExpression();
        }
        if (body == null) {
            parenthesizedExpression.delete();
            return;
        }
        final PsiElement parent = parenthesizedExpression.getParent();
        if(!(parent instanceof PsiExpression)){
            final PsiExpression newExpression =
                    (PsiExpression) parenthesizedExpression.replace(body);
            removeParentheses(newExpression, ignoreClarifyingParentheses);
            return;
        }
        final PsiExpression parentExpression = (PsiExpression) parent;
        final int parentPrecedence = getPrecedence(parentExpression);
        final int childPrecedence = getPrecedence(body);
        if(parentPrecedence < childPrecedence){
            final PsiElement bodyParent = body.getParent();
            final PsiParenthesizedExpression newParenthesizedExpression =
                    (PsiParenthesizedExpression)
                            parenthesizedExpression.replace(bodyParent);
            final PsiExpression expression =
                    newParenthesizedExpression.getExpression();
            if (expression != null) {
                removeParentheses(expression, ignoreClarifyingParentheses);
            }
        } else if(parentPrecedence == childPrecedence){
            if(parentExpression instanceof PsiBinaryExpression &&
               body instanceof PsiBinaryExpression){
                final PsiBinaryExpression parentBinaryExpression =
                        (PsiBinaryExpression)parentExpression;
                final IElementType parentOperator =
                        parentBinaryExpression.getOperationTokenType();
                final PsiBinaryExpression bodyBinaryExpression =
                        (PsiBinaryExpression)body;
                final IElementType bodyOperator =
                        bodyBinaryExpression.getOperationTokenType();
                final PsiType parentType = parentBinaryExpression.getType();
                final PsiType bodyType = body.getType();
                if(parentType != null && parentType.equals(bodyType) &&
                        parentOperator.equals(bodyOperator)) {
                    final PsiExpression rhs =
                            parentBinaryExpression.getROperand();
                    if (!PsiTreeUtil.isAncestor(rhs, body, true) ||
                            isCommutativeBinaryOperator(bodyOperator)) {
                        // use addAfter() + delete() instead of replace() to
                        // workaround automatic insertion of parentheses by psi
                        final PsiExpression newExpression = (PsiExpression)
                                parent.addAfter(body, parenthesizedExpression);
                        parenthesizedExpression.delete();
                        removeParentheses(newExpression,
                                ignoreClarifyingParentheses);
                        return;
                    }
                }
                if (ignoreClarifyingParentheses &&
                        !parentOperator.equals(bodyOperator)) {
                    removeParentheses(body, ignoreClarifyingParentheses);
                } else {
                    final PsiExpression newExpression = (PsiExpression)
                            parenthesizedExpression.replace(body);
                    removeParentheses(newExpression,
                            ignoreClarifyingParentheses);
                }
            } else{
                final PsiExpression newExpression =
                        (PsiExpression) parenthesizedExpression.replace(body);
                removeParentheses(newExpression, ignoreClarifyingParentheses);
            }
        } else{
            final PsiExpression newExpression =
                    (PsiExpression) parenthesizedExpression.replace(body);
            removeParentheses(newExpression, ignoreClarifyingParentheses);
        }
    }

    private static void removeParensFromConditionalExpression(
            @NotNull PsiConditionalExpression conditionalExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression condition = conditionalExpression.getCondition();
        removeParentheses(condition, ignoreClarifyingParentheses);
        final PsiExpression thenBranch =
                conditionalExpression.getThenExpression();
        if (thenBranch != null) {
            removeParentheses(thenBranch, ignoreClarifyingParentheses);
        }
        final PsiExpression elseBranch =
                conditionalExpression.getElseExpression();
        if (elseBranch != null) {
            removeParentheses(elseBranch, ignoreClarifyingParentheses);
        }
    }

    private static void removeParensFromInstanceOfExpression(
            @NotNull PsiInstanceOfExpression instanceofExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression operand = instanceofExpression.getOperand();
        removeParentheses(operand, ignoreClarifyingParentheses);
    }

    private static void removeParensFromBinaryExpression(
            @NotNull PsiBinaryExpression binaryExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression lhs = binaryExpression.getLOperand();
        removeParentheses(lhs, ignoreClarifyingParentheses);
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs != null) {
            removeParentheses(rhs, ignoreClarifyingParentheses);
        }
    }

    private static void removeParensFromPostfixExpression(
            @NotNull PsiPostfixExpression postfixExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression operand = postfixExpression.getOperand();
        removeParentheses(operand, ignoreClarifyingParentheses);
    }

    private static void removeParensFromPrefixExpression(
            @NotNull PsiPrefixExpression prefixExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression operand = prefixExpression.getOperand();
        if (operand != null) {
            removeParentheses(operand, ignoreClarifyingParentheses);
        }
    }

    private static void removeParensFromArrayAccessExpression(
            @NotNull PsiArrayAccessExpression arrayAccessExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression arrayExpression =
                arrayAccessExpression.getArrayExpression();
        removeParentheses(arrayExpression, ignoreClarifyingParentheses);
        final PsiExpression indexExpression =
                arrayAccessExpression.getIndexExpression();
        if (indexExpression != null) {
            removeParentheses(indexExpression, ignoreClarifyingParentheses);
        }
    }

    private static void removeParensFromTypeCastExpression(
            @NotNull PsiTypeCastExpression typeCastExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression operand = typeCastExpression.getOperand();
        if (operand != null) {
            removeParentheses(operand, ignoreClarifyingParentheses);
        }
    }

    private static void removeParensFromArrayInitializerExpression(
            @NotNull PsiArrayInitializerExpression arrayInitializerExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression[] initializers =
                arrayInitializerExpression.getInitializers();
        for (final PsiExpression initializer : initializers) {
            removeParentheses(initializer, ignoreClarifyingParentheses);
        }
    }

    private static void removeParensFromAssignmentExpression(
            @NotNull PsiAssignmentExpression assignment,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression lhs = assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        removeParentheses(lhs, ignoreClarifyingParentheses);
        if (rhs != null) {
            removeParentheses(rhs, ignoreClarifyingParentheses);
        }
    }

    private static void removeParensFromNewExpression(
            @NotNull PsiNewExpression newExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiExpression[] dimensions = newExpression.getArrayDimensions();
        for (PsiExpression dimension : dimensions) {
            removeParentheses(dimension, ignoreClarifyingParentheses);
        }
        final PsiExpression qualifier = newExpression.getQualifier();
        if(qualifier != null){
            removeParentheses(qualifier, ignoreClarifyingParentheses);
        }
        final PsiExpression arrayInitializer =
                newExpression.getArrayInitializer();
        if(arrayInitializer != null){
            removeParentheses(arrayInitializer, ignoreClarifyingParentheses);
        }
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if(argumentList != null){
            final PsiExpression[] arguments = argumentList.getExpressions();
            for (PsiExpression argument : arguments) {
                removeParentheses(argument, ignoreClarifyingParentheses);
            }
        }
    }

    private static void removeParensFromMethodCallExpression(
            @NotNull PsiMethodCallExpression methodCallExpression,
            boolean ignoreClarifyingParentheses)
            throws IncorrectOperationException {
        final PsiReferenceExpression target =
                methodCallExpression.getMethodExpression();
        final PsiExpressionList argumentList =
                methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        removeParentheses(target, ignoreClarifyingParentheses);
        for (final PsiExpression argument : arguments) {
            removeParentheses(argument, ignoreClarifyingParentheses);
        }
    }

    public static boolean areParenthesesNeeded(
            PsiParenthesizedExpression expression,
            boolean ignoreClarifyingParentheses) {
        final PsiElement parent = expression.getParent();
        final PsiElement child = expression.getExpression();
        if (parent instanceof PsiBinaryExpression &&
                child instanceof PsiBinaryExpression) {
            final PsiBinaryExpression parentBinaryExpression =
                    (PsiBinaryExpression)parent;
            final PsiBinaryExpression childBinaryExpression =
                    (PsiBinaryExpression)child;
            final IElementType childOperator =
                    childBinaryExpression.getOperationTokenType();
            final IElementType parentOperator =
                    parentBinaryExpression.getOperationTokenType();
            if (ignoreClarifyingParentheses &&
                    !childOperator.equals(parentOperator)) {
                return true;
            }
            final PsiType parentType =
                    parentBinaryExpression.getType();
            if (parentType == null) {
                return true;
            }
            final PsiType childType = childBinaryExpression.getType();
            if (!parentType.equals(childType)) {
                return true;
            }
            if (parentBinaryExpression.getROperand() == expression) {
                if (!isCommutativeBinaryOperator(
                        childOperator)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }
}