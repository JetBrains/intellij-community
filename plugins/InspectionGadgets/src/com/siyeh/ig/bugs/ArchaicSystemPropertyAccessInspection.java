package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class ArchaicSystemPropertyAccessInspection extends ExpressionInspection {
    public String getID(){
        return "UseOfArchaicSystemPropertyAccessors";
    }
    public String getDisplayName() {
        return "Use of archaic system property accessors";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {

        final PsiElement parent = location.getParent();
        assert parent != null;
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) parent.getParent();
        if(isIntegerGetInteger(call)){
            return "Call to Integer.#ref() accesses system properties, perhaps confusingly #loc";
        } else{
            return "Call to Boolean.#ref()accesses system properties, perhaps confusingly #loc";
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ArchaicSystemPropertyAccessVisitor();
    }

    private static class ArchaicSystemPropertyAccessVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            if(isIntegerGetInteger(expression) ||
                    isBooleanGetBoolean(expression)){
                registerMethodCallError(expression);
            }

        }
    }

    private static boolean isIntegerGetInteger(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null){
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"getInteger".equals(methodName) ) {
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
            return "java.lang.Integer".equals(className);
        }

        private static boolean isBooleanGetBoolean(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null){
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"getBoolean".equals(methodName) ) {
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
            return "java.lang.Boolean".equals(className);
        }

}
