package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.PsiThisExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class SynchronizeOnThisInspection extends MethodInspection {

    public String getDisplayName() {
        return "Synchronization on 'this'";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Synchronization on '#ref' may have unforseen side-effects #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SynchronizeOnThisVisitor(this, inspectionManager, onTheFly);
    }

    private static class SynchronizeOnThisVisitor extends BaseInspectionVisitor {
        private SynchronizeOnThisVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            final PsiExpression lockExpression = statement.getLockExpression();
            if (lockExpression == null) {
                return;
            }
            if (!(lockExpression instanceof PsiThisExpression)) {
                return;
            }
            registerError(lockExpression);
        }
    }

}
