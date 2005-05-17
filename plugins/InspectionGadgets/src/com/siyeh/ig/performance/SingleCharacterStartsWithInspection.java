package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class SingleCharacterStartsWithInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Single character .startsWith() or .endsWith()";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Single character #ref() should be replaced by .charAt() #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SingleCharacterStartsWithVisitor();
    }

    private static class SingleCharacterStartsWithVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"startsWith".equals(methodName) && !"endsWith".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 1 && args.length != 2) {
                return;
            }
            if (!isSingleCharacterStringLiteral(args[0])) {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final PsiType type = qualifier.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            registerMethodCallError(call);
        }

        private static boolean isSingleCharacterStringLiteral(PsiExpression arg) {
            final PsiType type = arg.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return false;
            }
            if (!(arg instanceof PsiLiteralExpression)) {
                return false;
            }
            final PsiLiteralExpression literal = (PsiLiteralExpression) arg;
            final String value = (String) literal.getValue();
            if (value == null) {
                return false;
            }
            return value.length() == 1;
        }

    }

}
