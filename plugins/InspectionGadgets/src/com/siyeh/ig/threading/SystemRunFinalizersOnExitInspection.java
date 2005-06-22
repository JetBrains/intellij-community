package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class SystemRunFinalizersOnExitInspection extends ExpressionInspection {
    public String getID(){
        return "CallToSystemRunFinalizersOnExit";
    }
    public String getDisplayName() {
        return "Call to 'System.runFinalizersOnExit()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {

        return "Call to System.#ref() #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemRunFinalizersOnExitVisitor();
    }

    private static class SystemRunFinalizersOnExitVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            if (!isRunFinalizersOnExit(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isRunFinalizersOnExit(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();

            final String methodName = methodExpression.getReferenceName();
            if (!"runFinalizersOnExit".equals(methodName) ) {
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return false;
            }
            final String className = aClass.getQualifiedName();
            if (className == null) {
                return false;
            }
            return "java.lang.System".equals(className);
        }
    }

}
