package com.siyeh.ig.performance;

import com.intellij.psi.*;

public class VariableIsModifiedVisitor extends PsiRecursiveElementVisitor {
    private boolean appendedTo = false;
    private final PsiVariable variable;

    public VariableIsModifiedVisitor(PsiVariable variable) {
        super();
        this.variable = variable;
    }


    public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
		final PsiMethod method = (PsiMethod)methodExpression.resolve();
		if (method == null) {
			return;
		}
		final PsiType returnType = method.getReturnType();
		if (returnType == null) {
			return;
		}
		final String canonicalText = returnType.getCanonicalText();
		if (!"java.lang.StringBuffer".equals(canonicalText) &&
                    !"java.lang.StringBuilder".equals(canonicalText))
		{
			return;
		}
		final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null) {
            return;
        }
        if (!(qualifier instanceof PsiReferenceExpression)) {
            return;
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression) qualifier;
        final PsiElement referent = reference.resolve();
        if (variable.equals(referent)) {
            appendedTo = true;
        }
    }


    public boolean isAppendedTo() {
        return appendedTo;
    }
}