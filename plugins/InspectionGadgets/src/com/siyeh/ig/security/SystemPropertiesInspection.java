package com.siyeh.ig.security;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
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

        return "Call to System.#ref() may pose security concerns #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemSetSecurityManagerVisitor();
    }

    private static class SystemSetSecurityManagerVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            if (!isSetSecurityManager(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isSetSecurityManager(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();

            final String methodName = methodExpression.getReferenceName();
            if (!"getProperty".equals(methodName) && !"getProperties".equals(methodName)) {
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
