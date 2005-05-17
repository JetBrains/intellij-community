package com.siyeh.ig.confusing;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class ConfusingElseInspection extends StatementInspection {
    public String getID(){
        return "ConfusingElseBranch";
    }
    public String getDisplayName() {
        return "Confusing 'else' branch";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryElseVisitor();
    }

    public String buildErrorString(PsiElement location) {
        return "#ref branch may be unwrapped, or the following statements placed in the else branch, as the if branch never completes #loc";
    }

    private static class UnnecessaryElseVisitor extends StatementInspectionVisitor {

        public void visitIfStatement(@NotNull PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            if (elseBranch instanceof PsiIfStatement) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
                return;
            }

            final PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);

            if (nextStatement == null) {
                return;
            }
            if (!ControlFlowUtils.statementMayCompleteNormally(elseBranch)) {
                return;         //protecting against an edge case where both branches return
                // and are followed by a case label
            }

            final PsiElement elseToken = statement.getElseElement();
            registerError(elseToken);
        }
    }
}
