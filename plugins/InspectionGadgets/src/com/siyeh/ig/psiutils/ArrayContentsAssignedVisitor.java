package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

public class ArrayContentsAssignedVisitor extends PsiRecursiveElementVisitor {
    private boolean assigned = false;
    private final PsiVariable variable;

    public ArrayContentsAssignedVisitor(PsiVariable variable) {
        super();
        this.variable = variable;
    }

    public void visitAssignmentExpression(PsiAssignmentExpression assignment){
        if(assigned)
        {
            return;
        }
        super.visitAssignmentExpression(assignment);
        final PsiExpression arg = assignment.getLExpression();
        if(!(arg instanceof PsiArrayAccessExpression)){
            return;
        }
        final PsiExpression arrayExpression =
                ((PsiArrayAccessExpression) arg).getArrayExpression();
        if(!(arrayExpression instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) arrayExpression).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            assigned = true;
        }
    }

    public void visitPrefixExpression(PsiPrefixExpression expression){
        super.visitPrefixExpression(expression);
        final PsiJavaToken operationSign = expression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if(!(tokenType.equals(JavaTokenType.PLUSPLUS) ||
                tokenType.equals(JavaTokenType.MINUSMINUS))){
            return;
        }
        final PsiExpression arg = expression.getOperand();
        if(!(arg instanceof PsiArrayAccessExpression)){
            return;
        }
        final PsiExpression arrayExpression =
                ((PsiArrayAccessExpression) arg).getArrayExpression();
        if(!(arrayExpression instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) arrayExpression).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            assigned = true;
        }
    }
    public void visitPostfixExpression(PsiPostfixExpression expression){
        super.visitPostfixExpression(expression);
        final PsiJavaToken operationSign = expression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if(!(tokenType.equals(JavaTokenType.PLUSPLUS) ||
                tokenType.equals(JavaTokenType.MINUSMINUS))){
            return;
        }
        final PsiExpression arg = expression.getOperand();
        if(!(arg instanceof PsiArrayAccessExpression)){
            return;
        }
        final PsiExpression arrayExpression =
                ((PsiArrayAccessExpression) arg).getArrayExpression();
        if(!(arrayExpression instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) arrayExpression).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            assigned = true;
        }
    }

    public boolean isAssigned() {
        return assigned;
    }
}
