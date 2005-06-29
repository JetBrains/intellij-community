package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class TextLabelInSwitchStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "Text label in 'switch' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Text label #ref: in 'switch' statement #loc ";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TextLabelInSwitchStatementVisitor();
    }

    private static class TextLabelInSwitchStatementVisitor extends StatementInspectionVisitor {

        public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            for(PsiStatement statement1 : statements){
                checkForLabel(statement1);
            }
        }

        private void checkForLabel(PsiStatement statement) {
            if (!(statement instanceof PsiLabeledStatement)) {
                return;
            }
            final PsiIdentifier label = ((PsiLabeledStatement) statement).getLabelIdentifier();
            registerError(label);
        }
    }

}