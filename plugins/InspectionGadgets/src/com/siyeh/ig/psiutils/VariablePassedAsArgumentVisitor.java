package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class VariablePassedAsArgumentVisitor extends PsiRecursiveElementVisitor {
    private boolean passed = false;
    private final PsiVariable variable;

    public VariablePassedAsArgumentVisitor(PsiVariable variable) {
        super();
        this.variable = variable;
    }

    public void visitReferenceExpression(PsiReferenceExpression ref) {
        final PsiExpression qualifier = ref.getQualifierExpression();
        if (qualifier != null) {
            qualifier.accept(this);
        }
        final PsiReferenceParameterList typeParameters = ref.getParameterList();
        if (typeParameters != null) {
            typeParameters.accept(this);
        }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        final PsiExpressionList argumentList = call.getArgumentList();
        if (argumentList == null) {
            return;
        }

        final PsiExpression[] args = argumentList.getExpressions();
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            final PsiExpression arg = args[i];
            if (arg instanceof PsiReferenceExpression) {
                final PsiElement referent = ((PsiReference) arg).resolve();
                if (referent != null && referent.equals(variable)) {
                    passed = true;
                }
            }
        }
    }

    public void visitNewExpression(PsiNewExpression newExpression) {
        super.visitNewExpression(newExpression);
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if (argumentList == null) {
            return;
        }
        final PsiExpression[] args = argumentList.getExpressions();
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            final PsiExpression arg = args[i];
            if (arg!=null && arg instanceof PsiReferenceExpression) {
                final PsiElement referent = ((PsiReference) arg).resolve();
                if (referent!=null && referent.equals(variable)) {
                    passed = true;
                }
            }
        }
    }

    public boolean isPassed() {
        return passed;
    }
}
