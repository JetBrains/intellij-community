package com.siyeh.ig.bugs;

import com.intellij.psi.*;

import java.util.HashSet;
import java.util.Set;

public class CollectionUpdateCalledVisitor extends PsiRecursiveElementVisitor {
    private static final Set updateNames = new HashSet(10);

    static {
        updateNames.add("add");
        updateNames.add("put");
        updateNames.add("set");
        updateNames.add("remove");
        updateNames.add("addAll");
        updateNames.add("removeAll");
        updateNames.add("retainAll");
        updateNames.add("putAll");
        updateNames.add("clear");
        updateNames.add("addElement");
        updateNames.add("removeAllElements");
        updateNames.add("trimToSize");
        updateNames.add("removeElementAt");
        updateNames.add("removeRange");
        updateNames.add("insertElementAt");
        updateNames.add("setElementAt");
        updateNames.add("removeRange");
    }

    private boolean updated = false;
    private final PsiVariable variable;

    public CollectionUpdateCalledVisitor(PsiVariable variable) {
        super();
        this.variable = variable;
    }


    public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        if (methodExpression == null) {
            return;
        }
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null) {
            return;
        }
        if (!(qualifier instanceof PsiReferenceExpression)) {
            return;
        }
        final PsiElement referent = ((PsiReference) qualifier).resolve();
        if (referent == null) {
            return;
        }
        if (!referent.equals(variable)) {
            return;
        }
        final String methodName = methodExpression.getReferenceName();
        if (updateNames.contains(methodName)) {
            updated = true;
        }
    }

    public boolean isUpdated() {
        return updated;
    }
}
