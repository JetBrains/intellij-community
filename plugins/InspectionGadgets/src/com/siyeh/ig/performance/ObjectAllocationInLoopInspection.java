package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ObjectAllocationInLoopsVisitor(this, inspectionManager, onTheFly);
    }

    private static class ObjectAllocationInLoopsVisitor extends BaseInspectionVisitor {
        private ObjectAllocationInLoopsVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

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
