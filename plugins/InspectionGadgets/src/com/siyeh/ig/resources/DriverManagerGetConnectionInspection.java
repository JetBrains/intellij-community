package com.siyeh.ig.resources;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new DriverManagerGetConnectionVisitor(this, inspectionManager, onTheFly);
    }

    private static class DriverManagerGetConnectionVisitor extends BaseInspectionVisitor {
        private DriverManagerGetConnectionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
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
