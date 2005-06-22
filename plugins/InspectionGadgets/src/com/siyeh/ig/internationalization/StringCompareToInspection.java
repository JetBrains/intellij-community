package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class StringCompareToInspection extends ExpressionInspection {
    public String getID(){
        return "CallToStringCompareTo";
    }
    public String getDisplayName() {
        return "Call to String.compareTo()";
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "String.#ref() called in an internationalized context #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringCompareToVisitor();
    }

    private static class StringCompareToVisitor extends BaseInspectionVisitor {


        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }

            if (!isStringCompareTo(expression)) {
                return;
            }

            registerMethodCallError(expression);
        }

        private static boolean isStringCompareTo(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"compareTo".equals(methodName)) {
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
            if (!TypeUtils.isJavaLangObject(parameterType) &&
                    !TypeUtils.isJavaLangString(parameterType)) {
                return false;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return false;
            }
            final String className = aClass.getQualifiedName();
            return "java.lang.String".equals(className);
        }
    }

}
