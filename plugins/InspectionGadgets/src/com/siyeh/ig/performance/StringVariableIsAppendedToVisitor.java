/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

class StringVariableIsAppendedToVisitor extends PsiRecursiveElementVisitor {
    private boolean appendedTo = false;
    private final PsiVariable variable;

    StringVariableIsAppendedToVisitor(PsiVariable variable) {
        super();
        this.variable = variable;
    }

    @Override public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
        if(appendedTo){
            return;
        }
        super.visitAssignmentExpression(assignment);
        final PsiExpression lhs = assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        if (rhs == null) {
            return;
        }
        if (!(lhs instanceof PsiReferenceExpression)) {
            return;
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
        final PsiElement referent = reference.resolve();
        if (!variable.equals(referent)) {
            return;
        }
        final PsiJavaToken operationSign = assignment.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if (tokenType.equals(JavaTokenType.PLUSEQ)) {
            appendedTo = true;
        } else if (isConcatenation(rhs)) {
            appendedTo = true;
        }
    }

    private boolean isConcatenation(PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof PsiReferenceExpression) {
            final PsiElement referent = ((PsiReference) expression).resolve();
            return variable.equals(referent);
        }
        if (expression instanceof PsiParenthesizedExpression) {
            final PsiExpression body =
                    ((PsiParenthesizedExpression) expression).getExpression();
            return isConcatenation(body);
        }
        if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            return isConcatenation(lhs) || isConcatenation(rhs);
        }
        return false;
    }

    public boolean isAppendedTo() {
        return appendedTo;
    }
}
