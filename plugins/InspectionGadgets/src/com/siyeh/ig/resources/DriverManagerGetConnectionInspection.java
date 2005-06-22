package com.siyeh.ig.resources;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class DriverManagerGetConnectionInspection extends ExpressionInspection {
    public String getID(){
        return "CallToDriverManagerGetConnection";
    }

    public String getDisplayName() {
        return "Use of DriverManager to get JDBC connection";
    }

    public String getGroupDisplayName() {
        return GroupNames.RESOURCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to DriverManager.#ref() #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DriverManagerGetConnectionVisitor();
    }

    private static class DriverManagerGetConnectionVisitor extends BaseInspectionVisitor {


        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            if (!isDriverManagerGetConnection(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isDriverManagerGetConnection(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();

            final String methodName = methodExpression.getReferenceName();
            if (!"getConnection".equals(methodName) ) {
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
            return "java.sql.DriverManager".equals(className);
        }
    }

}
