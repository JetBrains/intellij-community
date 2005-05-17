package com.siyeh.ig.threading;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSynchronizedStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class EmptySynchronizedStatementInspection extends StatementInspection {
    public String getDisplayName() {
        return "Empty 'synchronized' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Empty #ref statement #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptySynchronizedStatementVisitor();
    }

    private static class EmptySynchronizedStatementVisitor extends StatementInspectionVisitor {

        public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements == null) {
                return;
            }
            if (statements.length > 0) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
