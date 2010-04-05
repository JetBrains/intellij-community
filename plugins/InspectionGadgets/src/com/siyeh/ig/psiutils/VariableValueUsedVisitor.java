/*
 * Copyright 2008-2010 Dave Griffith, Bas Leijdekkers
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
import org.jetbrains.annotations.NotNull;

class VariableValueUsedVisitor extends JavaRecursiveElementVisitor {

    @NotNull
    private final PsiVariable variable;
    private boolean read = false;
    private boolean written = false;

    VariableValueUsedVisitor(@NotNull PsiVariable variable) {
        this.variable = variable;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (read || written) {
            return;
        }
        super.visitElement(element);
    }

    @Override
    public void visitAssignmentExpression(
            @NotNull PsiAssignmentExpression assignment) {
        if (read || written) {
            return;
        }
        super.visitAssignmentExpression(assignment);
        final PsiExpression lhs = assignment.getLExpression();
        if (lhs instanceof PsiReferenceExpression) {
            PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) lhs;
            final PsiElement target = referenceExpression.resolve();
            if (variable.equals(target)) {
                written = true;
                return;
            }
        }
        final PsiExpression rhs = assignment.getRExpression();
        if (rhs == null) {
            return;
        }
        final VariableUsedVisitor visitor =
                new VariableUsedVisitor(variable);
        rhs.accept(visitor);
        read = visitor.isUsed();
    }

    @Override
    public void visitPrefixExpression(
            @NotNull PsiPrefixExpression prefixExpression) {
        if (read || written) {
            return;
        }
        super.visitPrefixExpression(prefixExpression);
        final PsiJavaToken operationSign =
                prefixExpression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
            return;
        }
        final PsiExpression operand = prefixExpression.getOperand();
        if (!(operand instanceof PsiReferenceExpression)) {
            return;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) operand;
        final PsiElement target = referenceExpression.resolve();
        if (!variable.equals(target)) {
            return;
        }
        written = true;
    }

    @Override
    public void visitPostfixExpression(
            @NotNull PsiPostfixExpression postfixExpression) {
        if (read || written) {
            return;
        }
        super.visitPostfixExpression(postfixExpression);
        final PsiJavaToken operationSign =
                postfixExpression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
            return;
        }
        final PsiExpression operand = postfixExpression.getOperand();
        if (!(operand instanceof PsiReferenceExpression)) {
            return;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) operand;
        final PsiElement target = referenceExpression.resolve();
        if (!variable.equals(target)) {
            return;
        }
        written = true;
    }

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
        if (read || written) {
            return;
        }
        super.visitVariable(variable);
        final PsiExpression initalizer = variable.getInitializer();
        if (initalizer == null) {
            return;
        }
        final VariableUsedVisitor visitor =
                new VariableUsedVisitor(variable);
        initalizer.accept(visitor);
        read = visitor.isUsed();
    }

    @Override
    public void visitMethodCallExpression(
            @NotNull PsiMethodCallExpression call) {
        if (read || written) {
            return;
        }
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        final VariableUsedVisitor visitor =
                new VariableUsedVisitor(variable);
        if (qualifier != null ) {
            qualifier.accept(visitor);
            if (visitor.isUsed()) {
                read = true;
                return;
            }
        }
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        for (final PsiExpression argument : arguments) {
            argument.accept(visitor);
            if (visitor.isUsed()) {
                read = true;
                return;
            }
        }
    }

    @Override
    public void visitNewExpression(
            @NotNull PsiNewExpression newExpression) {
        if (read || written) {
            return;
        }
        super.visitNewExpression(newExpression);
        final PsiExpressionList argumentList =
                newExpression.getArgumentList();
        if (argumentList == null) {
            return;
        }
        final PsiExpression[] arguments = argumentList.getExpressions();
        for (final PsiExpression argument : arguments) {
            final VariableUsedVisitor visitor =
                    new VariableUsedVisitor(variable);
            argument.accept(visitor);
            if (visitor.isUsed()) {
                read = true;
                return;
            }
        }
    }

    @Override
    public void visitArrayInitializerExpression(
            PsiArrayInitializerExpression expression) {
        if (read || written) {
            return;
        }
        super.visitArrayInitializerExpression(expression);
        final PsiExpression[] arguments = expression.getInitializers();
        for (final PsiExpression argument : arguments) {
            final VariableUsedVisitor visitor =
                    new VariableUsedVisitor(variable);
            argument.accept(visitor);
            if (visitor.isUsed()) {
                read = true;
                return;
            }
        }
    }

    @Override
    public void visitReturnStatement(
            @NotNull PsiReturnStatement returnStatement) {
        if (read || written) {
            return;
        }
        super.visitReturnStatement(returnStatement);
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null) {
            return;
        }
        final VariableUsedVisitor visitor =
                new VariableUsedVisitor(variable);
        returnValue.accept(visitor);
        read = visitor.isUsed();
    }

    /**
     * check if variable is used in nested/inner class.
     */
    @Override
    public void visitClass(PsiClass aClass) {
        if (read || written) {
            return;
        }
        super.visitClass(aClass);
        final VariableUsedVisitor visitor =
                new VariableUsedVisitor(variable);
        aClass.accept(visitor);
        read = visitor.isUsed();
    }

    public boolean isVariableValueUsed() {
        return read;
    }
}
