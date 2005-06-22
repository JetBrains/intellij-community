package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectEqualsNullVisitor();
    }

    private static class ObjectEqualsNullVisitor extends BaseInspectionVisitor {


        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            if(!IsEqualsUtil.isEquals(call)){
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            assert argumentList != null;
            final PsiExpression[] args = argumentList.getExpressions();
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
