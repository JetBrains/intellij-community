package com.siyeh.ig.confusing;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLabeledStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;

public class LabeledStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "Labeled statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Labeled statement #ref: #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LabeledStatementVisitor();
    }

    private static class LabeledStatementVisitor extends StatementInspectionVisitor {

        public void visitLabeledStatement(PsiLabeledStatement statement) {
            super.visitLabeledStatement(statement);
            PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            registerError(labelIdentifier);

        }

    }

}
