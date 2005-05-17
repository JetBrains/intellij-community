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
