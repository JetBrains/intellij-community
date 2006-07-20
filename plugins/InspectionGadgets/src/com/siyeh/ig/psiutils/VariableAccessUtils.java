/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
import org.jetbrains.annotations.NotNull;

public class VariableAccessUtils{

    private VariableAccessUtils(){
        super();
    }

    public static boolean variableIsAssignedFrom(@NotNull PsiVariable variable,
                                                 PsiElement context){
        final VariableAssignedFromVisitor visitor =
                new VariableAssignedFromVisitor(variable);
        context.accept(visitor);
        return visitor.isAssignedFrom();
    }

    public static boolean variableIsPassedAsMethodArgument(
            @NotNull PsiVariable variable, @NotNull PsiElement context) {
        final VariablePassedAsArgumentVisitor visitor =
                new VariablePassedAsArgumentVisitor(variable);
        context.accept(visitor);
        return visitor.isPassed();
    }

    public static boolean variableIsUsedInArrayInitializer(
            @NotNull PsiVariable variable, @NotNull PsiElement context) {
        final VariableUsedInArrayInitializerVisitor visitor =
                new VariableUsedInArrayInitializerVisitor(variable);
        context.accept(visitor);
        return visitor.isPassed();
    }

    public static boolean variableIsAssigned(
            @NotNull PsiVariable variable, @NotNull PsiElement context) {
        final VariableAssignedVisitor visitor =
                new VariableAssignedVisitor(variable);
        context.accept(visitor);
        return visitor.isAssigned();
    }

    public static boolean variableIsReturned(
            @NotNull PsiVariable variable, @NotNull PsiElement context) {
        final VariableReturnedVisitor visitor =
                new VariableReturnedVisitor(variable);
        context.accept(visitor);
        return visitor.isReturned();
    }

    public static boolean arrayContentsAreAccessed(
            @NotNull PsiVariable variable, @NotNull PsiElement context) {
        final ArrayContentsAccessedVisitor visitor =
                new ArrayContentsAccessedVisitor(variable);
        context.accept(visitor);
        return visitor.isAccessed();
    }

    public static boolean arrayContentsAreAssigned(
            @NotNull PsiVariable variable, @NotNull PsiElement context) {
        final ArrayContentsAssignedVisitor visitor =
                new ArrayContentsAssignedVisitor(variable);
        context.accept(visitor);
        return visitor.isAssigned();
    }

    public static boolean variableIsUsedInInnerClass(
            @NotNull PsiVariable variable, @NotNull PsiElement context) {
        final VariableUsedInInnerClassVisitor visitor =
                new VariableUsedInInnerClassVisitor(variable);
        context.accept(visitor);
        return visitor.isUsedInInnerClass();
    }

    public static boolean mayEvaluateToVariable(PsiExpression expression,
                                                PsiVariable variable){
        if(expression == null){
            return false;
        }
        if(expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiExpression lOperand = binaryExpression.getLOperand();
            final PsiExpression rOperand = binaryExpression.getROperand();
            return mayEvaluateToVariable(lOperand, variable) ||
                    mayEvaluateToVariable(rOperand, variable);
        }
        if(expression instanceof PsiParenthesizedExpression){
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression)expression;
            final PsiExpression containedExpression =
                    parenthesizedExpression.getExpression();
            return mayEvaluateToVariable(containedExpression, variable);
        }
        if(expression instanceof PsiTypeCastExpression){
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression)expression;
            final PsiExpression containedExpression =
                    typeCastExpression.getOperand();
            return mayEvaluateToVariable(containedExpression, variable);
        }
        if(expression instanceof PsiConditionalExpression){
            final PsiConditionalExpression conditional =
                    (PsiConditionalExpression) expression;
            final PsiExpression thenExpression = conditional.getThenExpression();
            final PsiExpression elseExpression = conditional.getElseExpression();
            return mayEvaluateToVariable(thenExpression, variable) ||
                    mayEvaluateToVariable(elseExpression, variable);
        }
        if(expression instanceof PsiArrayAccessExpression){
            final PsiArrayAccessExpression arrayAccessExpression =
                    (PsiArrayAccessExpression)expression;
            final PsiExpression arrayExpression =
                    arrayAccessExpression.getArrayExpression();
            return mayEvaluateToVariable(arrayExpression, variable);
        }
        return evaluatesToVariable(expression, variable);
    }

    public static boolean evaluatesToVariable(
            PsiExpression expression, PsiVariable variable) {
        final PsiExpression strippedExpression =
                ParenthesesUtils.stripParentheses(expression);
        if(strippedExpression == null){
            return false;
        }
        if (!(expression instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) expression;
        final PsiElement referent = referenceExpression.resolve();
        if(referent == null){
            return false;
        }
        return referent.equals(variable);
    }

    public static boolean variableIsUsed(PsiVariable variable,
                                         PsiElement context){
        final VariableUsedVisitor visitor
                = new VariableUsedVisitor(variable);
        context.accept(visitor);
        return visitor.isUsed();
    }
}