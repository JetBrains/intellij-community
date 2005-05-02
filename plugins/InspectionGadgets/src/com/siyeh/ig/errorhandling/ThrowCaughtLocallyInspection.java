package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;

public class ThrowCaughtLocallyInspection extends StatementInspection {

    public String getDisplayName() {
        return "'throw' caught by containing 'try' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' caught by containing 'try' statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ThrowCaughtLocallyVisitor(this, inspectionManager, onTheFly);
    }

    private static class ThrowCaughtLocallyVisitor extends StatementInspectionVisitor {
        private ThrowCaughtLocallyVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            final PsiExpression exception = statement.getException();
            if (exception == null) {
                return;
            }
            final PsiType exceptionType = exception.getType();
            if (exceptionType == null) {
                return;
            }

            PsiTryStatement containingTryStatement =
                    PsiTreeUtil.getParentOfType(statement, PsiTryStatement.class);
            while (containingTryStatement != null) {
                final PsiCodeBlock tryBlock = containingTryStatement.getTryBlock();
                if (PsiTreeUtil.isAncestor(tryBlock, statement, true)) {
                    final PsiParameter[] catchBlockParameters =
                            containingTryStatement.getCatchBlockParameters();
                    for(final PsiParameter parameter : catchBlockParameters){
                        final PsiType parameterType = parameter.getType();
                        if(parameterType.isAssignableFrom(exceptionType)){
                            registerStatementError(statement);
                            return;
                        }
                    }
                }
                containingTryStatement =
                        PsiTreeUtil.getParentOfType(containingTryStatement, PsiTryStatement.class);
            }
        }
    }

}
