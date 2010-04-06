/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

class VariableAssignedVisitor extends JavaRecursiveElementVisitor{

    @NotNull private final Collection<PsiVariable> variables;
    private final boolean recurseIntoClasses;
    private boolean assigned = false;

    public VariableAssignedVisitor(@NotNull Collection<PsiVariable> variables,
                                   boolean recurseIntoClasses){
        this.variables = variables;
        this.recurseIntoClasses = recurseIntoClasses;
    }

    @Override public void visitElement(@NotNull PsiElement element){
        if(assigned){
            return;
        }
        super.visitElement(element);
    }

    @Override public void visitAssignmentExpression(
            @NotNull PsiAssignmentExpression assignment){
        if(assigned){
            return;
        }
        super.visitAssignmentExpression(assignment);
        final PsiExpression lhs = assignment.getLExpression();
        for (PsiVariable variable : variables) {
            if(mayEvaluateToVariable(lhs, variable)){
                assigned = true;
            }
        }
    }

    @Override
    public void visitClass(PsiClass aClass) {
        if(!recurseIntoClasses){
            return;
        }
        if(assigned){
            return;
        }
        super.visitClass(aClass);
    }

    @Override public void visitPrefixExpression(
            @NotNull PsiPrefixExpression prefixExpression){
        if(assigned){
            return;
        }
        super.visitPrefixExpression(prefixExpression);
        final PsiJavaToken operationSign = prefixExpression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                   !tokenType.equals(JavaTokenType.MINUSMINUS)){
            return;
        }
        final PsiExpression operand = prefixExpression.getOperand();
        for (PsiVariable variable : variables) {
            if(mayEvaluateToVariable(operand, variable)){
                assigned = true;
            }
        }
    }

    @Override public void visitPostfixExpression(
            @NotNull PsiPostfixExpression postfixExpression){
        if(assigned){
            return;
        }
        super.visitPostfixExpression(postfixExpression);
        final PsiJavaToken operationSign = postfixExpression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                   !tokenType.equals(JavaTokenType.MINUSMINUS)){
            return;
        }
        final PsiExpression operand = postfixExpression.getOperand();
        for (PsiVariable variable : variables) {
            if(mayEvaluateToVariable(operand, variable)){
                assigned = true;
            }
        }
    }

    public static boolean mayEvaluateToVariable(
            @Nullable PsiExpression expression,
            @NotNull PsiVariable variable) {
        if (expression == null){
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
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiArrayAccessExpression){
                return false;
            }
            final PsiType type = variable.getType();
            if (!(type instanceof PsiArrayType)) {
                return false;
            }
            final PsiArrayType arrayType = (PsiArrayType)type;
            final int dimensions = arrayType.getArrayDimensions();
            if (dimensions <= 1) {
                return false;
            }
            PsiArrayAccessExpression arrayAccessExpression =
                    (PsiArrayAccessExpression)expression;
            PsiExpression arrayExpression =
                    arrayAccessExpression.getArrayExpression();
            int count = 1;
            while (arrayExpression instanceof PsiArrayAccessExpression) {
                arrayAccessExpression =
                        (PsiArrayAccessExpression)arrayExpression;
                arrayExpression = arrayAccessExpression.getArrayExpression();
                count++;
            }
            return count != dimensions &&
                   mayEvaluateToVariable(arrayExpression, variable);
        }
        return evaluatesToVariable(expression, variable);
    }

    public static boolean evaluatesToVariable(
            @Nullable PsiExpression expression,
            @NotNull PsiVariable variable) {
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
        return variable.equals(referent);
    }

    public boolean isAssigned(){
        return assigned;
    }
}