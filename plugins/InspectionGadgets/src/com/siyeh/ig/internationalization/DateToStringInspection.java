package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;

public class DateToStringInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Call to Date.toString()";
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Date.#ref() used in an internationalized context #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new DateToStringVisitor(this, inspectionManager, onTheFly);
    }

    private static class DateToStringVisitor extends BaseInspectionVisitor {
        private DateToStringVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final String methodName = MethodCallUtils.getMethodName(expression);
            if (!"toString".equals(methodName)) {
                return;
            }
            final PsiType targetType = MethodCallUtils.getTargetType(expression);
            if (!TypeUtils.typeEquals("java.util.Date", targetType)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            if (argumentList.getExpressions().length != 0) {
                return;
            }
            registerMethodCallError(expression);
        }
    }

}
