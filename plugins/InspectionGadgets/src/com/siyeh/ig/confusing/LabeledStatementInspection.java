package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;

public class LabeledStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "Labeled statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Labeled statement #ref: #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new LabeledStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class LabeledStatementVisitor extends StatementInspectionVisitor {
        private LabeledStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLabeledStatement(PsiLabeledStatement statement) {
            super.visitLabeledStatement(statement);
            PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            registerError(labelIdentifier);

        }

    }

}
