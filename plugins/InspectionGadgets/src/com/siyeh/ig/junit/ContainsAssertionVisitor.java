package com.siyeh.ig.junit;

import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;

public class ContainsAssertionVisitor extends PsiRecursiveElementVisitor {
    private boolean containsAssertion = false;

    public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        if(methodExpression == null)
        {
            return;
        }
        final String methodName = methodExpression.getReferenceName();
        if(methodName.startsWith("assert") ||methodName.startsWith("fail"))
        {
             containsAssertion = true;
        }
    }

    public boolean containsAssertion() {
        return containsAssertion;
    }

}
