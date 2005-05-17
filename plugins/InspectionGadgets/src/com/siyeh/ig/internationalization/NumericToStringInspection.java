package com.siyeh.ig.internationalization;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class NumericToStringInspection extends ExpressionInspection {
    public String getID(){
        return "CallToNumericToString";
    }
    public String getDisplayName() {
        return "Call to Numeric .toString()";
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Numeric .#ref()  called in an internationalized context #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NumericToStringVisitor();
    }

    private static class NumericToStringVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"toString".equals(methodName)) {
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
            final String className = aClass.getQualifiedName();
            if (!TypeConversionUtil.isPrimitiveWrapper(className)) {
                return;
            }
            registerMethodCallError(expression);
        }
    }

}
