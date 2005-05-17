package com.siyeh.ig.confusing;

import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ContinueStatementWithLabelInspection extends StatementInspection {

    public String getDisplayName() {
        return "'continue' statement with label";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref statement with label #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ContinueStatementVisitor();
    }

    private static class ContinueStatementVisitor extends StatementInspectionVisitor {
       
        public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
            super.visitContinueStatement(statement);
            final PsiIdentifier label = statement.getLabelIdentifier();
            if (label == null) {
                return;
            }
            final String labelText = label.getText();
            if (labelText == null) {
                return;
            }
            if (labelText.length() == 0) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
