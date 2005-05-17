package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;

public class DoubleCheckedLockingInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Double-checked locking";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Double-checked locking #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DoubleCheckedLockingVisitor();
    }

    private static class DoubleCheckedLockingVisitor extends BaseInspectionVisitor {

        public void visitIfStatement(@NotNull PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiExpression outerCondition = statement.getCondition();
            if (outerCondition == null) {
                return;
            }
            if (SideEffectChecker.mayHaveSideEffects(outerCondition)) {
                return;
            }
            PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            thenBranch = ControlFlowUtils.stripBraces(thenBranch);
            if (!(thenBranch instanceof PsiSynchronizedStatement)) {
                return;
            }
            final PsiSynchronizedStatement syncStatement =
                    (PsiSynchronizedStatement) thenBranch;
            final PsiCodeBlock body = syncStatement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements == null) {
                return;
            }
            if (statements.length != 1) {
                return;
            }
            if (!(statements[0] instanceof PsiIfStatement)) {
                return;
            }
            final PsiIfStatement innerIf = (PsiIfStatement) statements[0];
            final PsiExpression innerCondition = innerIf.getCondition();
            if (!EquivalenceChecker.expressionsAreEquivalent(innerCondition, outerCondition)) {
                return;
            }
            registerStatementError(statement);
        }
    }
}
