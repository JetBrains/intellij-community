package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class SynchronizeOnNonFinalFieldInspection extends MethodInspection {

    public String getDisplayName() {
        return "Synchronization on a non-final field";
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Synchronization on a non-final field #ref is unlikely to have useful semantics #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SynchronizeOnNonFinalFieldVisitor(this, inspectionManager, onTheFly);
    }

    private static class SynchronizeOnNonFinalFieldVisitor extends BaseInspectionVisitor {
        private SynchronizeOnNonFinalFieldVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSynchronizedStatement(PsiSynchronizedStatement statement){
            super.visitSynchronizedStatement(statement);
            final PsiExpression lockExpression = statement.getLockExpression();
            if(!(lockExpression instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReference reference = lockExpression.getReference();
            if(reference == null){
                return;
            }
            final PsiElement element = reference.resolve();
            if(!(element instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) element;
            if(field.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            registerError(lockExpression);
        }
    }

}
