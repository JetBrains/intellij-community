package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class ObjectEqualsNullInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Object.equals(null)";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return ".equals(#ref) is probably not what was intended #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ObjectEqualsNullVisitor(this, inspectionManager, onTheFly);
    }

    private static class ObjectEqualsNullVisitor extends BaseInspectionVisitor {
        private ObjectEqualsNullVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"equals".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 1) {
                return;
            }
            if (!isNull(args[0])) {
                return;
            }
            registerError(args[0]);
        }

        private static boolean isNull(PsiExpression arg) {
            if (!(arg instanceof PsiLiteralExpression)) {
                return false;
            }
            final String text = arg.getText();
            return "null".equals(text);
        }

    }

}
