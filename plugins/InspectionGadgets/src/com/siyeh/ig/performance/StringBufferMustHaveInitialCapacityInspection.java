package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class StringBufferMustHaveInitialCapacityInspection extends ExpressionInspection {
    public String getID(){
        return "StringBufferWithoutInitialCapacity";
    }

    public String getDisplayName() {
        return "StringBuffer or StringBuilder without initial capacity";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref without initial capacity #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StringBufferInitialCapacityVisitor(this, inspectionManager, onTheFly);
    }

    private static class StringBufferInitialCapacityVisitor extends BaseInspectionVisitor {
        private StringBufferInitialCapacityVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();

            if (!TypeUtils.typeEquals("java.lang.StringBuffer", type) &&
                    !TypeUtils.typeEquals("java.lang.StringBuilder", type)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if (args == null) {
                return;
            }
            if (args.length != 0) {
                return;
            }
            registerError(expression);
        }
    }

}
