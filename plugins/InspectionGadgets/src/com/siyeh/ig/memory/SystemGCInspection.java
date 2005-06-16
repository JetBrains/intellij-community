package com.siyeh.ig.memory;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class SystemGCInspection extends ExpressionInspection {
    public String getID(){
        return "CallToSystemGC";
    }

    public String getDisplayName() {
        return "Calls to System.gc() or Runtime.gc()";
    }

    public String getGroupDisplayName() {
        return GroupNames.MEMORY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref should not be called in production code #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemGCVisitor();
    }

    private static class SystemGCVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"gc".equals(methodName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 0) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null)
            {
                return;
            }
            final String className = aClass.getQualifiedName();
            if (!"java.lang.System".equals(className) &&
                    !"java.lang.Runtime".equals(className)) {
                return;
            }
            registerError(expression);
        }
    }

}
