package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class ConfusingElseInspection extends StatementInspection {

    public String getDisplayName() {
        return "Confusing else branch";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    protected BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryElseVisitor(this, inspectionManager, onTheFly);
    }

    public String buildErrorString(PsiElement location) {
        return "#ref branch may be unwrapped, or the following statements placed in the else branch, as the if branch never completes #loc";
    }

    private static class UnnecessaryElseVisitor extends BaseInspectionVisitor {
        private UnnecessaryElseVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitIfStatement(PsiIfStatement statement) {
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

            final PsiStatement nextStatement = findNextStatement(statement);

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

        private static PsiStatement findNextStatement(PsiStatement statement) {
            PsiElement next = statement.getNextSibling();
            while (next != null) {
                if (next instanceof PsiStatement) {
                    return (PsiStatement) next;
                }
                next = next.getNextSibling();
            }
            return null;
        }

    }

}
