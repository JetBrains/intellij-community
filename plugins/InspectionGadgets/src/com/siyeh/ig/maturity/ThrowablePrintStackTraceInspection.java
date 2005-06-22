package com.siyeh.ig.maturity;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

public class ThrowablePrintStackTraceInspection extends ExpressionInspection {
    public String getID(){
        return "CallToPrintStackTrace";
    }
    public String getDisplayName() {
        return "Call to 'printStackTrace()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to #ref() should probably be replaced with more robust logging #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThrowablePrintStackTraceVisitor();
    }

    private static class ThrowablePrintStackTraceVisitor extends BaseInspectionVisitor {
       
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final String methodName = MethodCallUtils.getMethodName(expression);
            if (!"printStackTrace".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            if (argumentList.getExpressions().length != 0) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            registerMethodCallError(expression);
        }

    }

}
