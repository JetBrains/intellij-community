package com.siyeh.ig.portability;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class SystemExitInspection extends ExpressionInspection {
    public String getID(){
        return "CallToSystemExit";
    }
    public String getDisplayName() {
        return "Call to 'System.exit()' or related methods";
    }

    public String getGroupDisplayName() {
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiIdentifier methodNameIdentifier = (PsiIdentifier) location;
        final PsiReference methodExpression = (PsiReference) methodNameIdentifier.getParent();
        final PsiMethod method = (PsiMethod) methodExpression.resolve();
        final PsiClass containingClass = method.getContainingClass();
        return "Call to " + containingClass.getName() + ".#ref() is non-portable #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SystemExitVisitor(this, inspectionManager, onTheFly);
    }

    private static class SystemExitVisitor extends BaseInspectionVisitor {
        private SystemExitVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            if (!isSystemExit(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isSystemExit(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();

            final String methodName = methodExpression.getReferenceName();
            if (!"exit".equals(methodName) && !"halt".equals(methodName)) {
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }

            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return false;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 1) {
                return false;
            }
            final PsiType parameterType = parameters[0].getType();
            if (!parameterType.equals(PsiType.INT)) {
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
            if (!"java.lang.System".equals(className) &&
                    !"java.lang.Runtime".equals(className)) {
                return false;
            }
            return true;
        }
    }

}
