package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class ObjectAllocationInLoopInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Object allocation in loop";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Object allocation (#ref) in loop #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectAllocationInLoopsVisitor();
    }

    private static class ObjectAllocationInLoopsVisitor extends BaseInspectionVisitor {


        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);

            if (!ControlFlowUtils.isInLoop(expression)) {
                return;
            }
            if (ControlFlowUtils.isInExitStatement(expression)) {
                return;
            }
            registerError(expression);
        }

    }

}
