package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class SystemOutErrInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Use of System.out or System.err";
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Uses of #ref should probably be replaced with more robust logging #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SystemOutErrVisitor(this, inspectionManager, onTheFly);
    }

    private static class SystemOutErrVisitor extends BaseInspectionVisitor {
        private SystemOutErrVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);

            final String text = expression.getText();
            if (text == null) {
                return;
            }
            if (!"System.out".equals(text) &&
                    !"System.err".equals(text)) {
                return;
            }
            registerError(expression);
        }

    }

}
