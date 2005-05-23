package com.siyeh.ig.security;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class SystemPropertiesInspection extends ExpressionInspection {
    public String getID(){
        return "AccessOfSystemProperties";
    }
    public String getDisplayName() {
        return "Access of system properties";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {

        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) location.getParent().getParent();
        if(isGetSystemProperty(call)){
            return "Call to System.#ref() may pose security concerns #loc";
        } else if(isIntegerGetInteger(call)){
            return "Call to Integer.#ref() may pose security concerns #loc";
        } else{
            return "Call to Boolean.#ref() may pose security concerns #loc";
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemSetSecurityManagerVisitor();
    }

    private static class SystemSetSecurityManagerVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            if(isGetSystemProperty(expression) ||
                    isIntegerGetInteger(expression) ||
                    isBooleanGetBoolean(expression)){
                registerMethodCallError(expression);
            }

        }
    }

    private static boolean isGetSystemProperty(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null){
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"getProperty".equals(methodName)
                    && !"getProperties".equals(methodName)
                    && !"setProperty".equals(methodName)
                    && !"setProperties".equals(methodName)
                    && !"clearProperties".equals(methodName)
                    ) {
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
