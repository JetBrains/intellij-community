package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class InstanceofCatchParameterInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "'instanceof' on 'catch' parameter";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'instanceof' on 'catch' parameter #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new InstanceofCatchParameterVisitor(this, inspectionManager, onTheFly);
    }

    private static class InstanceofCatchParameterVisitor extends BaseInspectionVisitor {
        private InstanceofCatchParameterVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression exp){
            super.visitInstanceOfExpression(exp);
            if(!ControlFlowUtils.isInCatchBlock(exp)){
                return;
            }

            final PsiExpression operand = exp.getOperand();
            if(!(operand instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression ref = (PsiReferenceExpression) operand;
            final PsiElement referent = ref.resolve();
            if(!(referent instanceof PsiParameter)){
                return;
            }
            if(!(((PsiParameter) referent).getDeclarationScope() instanceof PsiCatchSection)){
                return;
            }
            registerError(exp);
        }

    }

}
