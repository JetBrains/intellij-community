package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class NonThreadSafeLazyInitializationInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Non-thread-safe lazy initialization of static field";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Lazy initialization of static field '#ref' is not thread-safe #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new DoubleCheckedLockingVisitor(this, inspectionManager, onTheFly);
    }

    private static class DoubleCheckedLockingVisitor extends BaseInspectionVisitor {
        private DoubleCheckedLockingVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression){
            super.visitAssignmentExpression(expression);
        }

    }
}
