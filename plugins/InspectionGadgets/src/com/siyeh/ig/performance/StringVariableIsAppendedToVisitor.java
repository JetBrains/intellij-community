package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

public class StringVariableIsAppendedToVisitor extends PsiRecursiveElementVisitor {
    private boolean appendedTo = false;
    private final PsiVariable variable;

    public StringVariableIsAppendedToVisitor(PsiVariable variable) {
        super();
        this.variable = variable;
    }

    public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
        super.visitAssignmentExpression(assignment);
        final PsiExpression lhs = assignment.getLExpression();
        if (lhs == null) {
            return;
        }
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
        if (operationSign == null) {
            return;
        }
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
