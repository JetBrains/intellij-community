package com.siyeh.ig.internationalization;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class StringToUpperWithoutLocaleInspection extends ExpressionInspection {
    public String getID(){
        return "StringToUpperCaseOrToLowerCaseWithoutLocale";
    }
    public String getDisplayName() {
        return "Call to String.toUpperCase() or .toLowerCase() without a Locale";
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "String.#ref() called without specifying a Locale in an internationalized context #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringToUpperWithoutLocaleVisitor();
    }

    private static class StringToUpperWithoutLocaleVisitor extends BaseInspectionVisitor {
     
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"toUpperCase".equals(methodName) && !"toLowerCase".equals(methodName)) {
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
            if (parameters.length == 1) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null)
            {
                return;
            }
            final String className = containingClass.getQualifiedName();
            if(!"java.lang.String".equals(className))
            {
                return;
            }
            registerMethodCallError(expression);
        }
    }

}
