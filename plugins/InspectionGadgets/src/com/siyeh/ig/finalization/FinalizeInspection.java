package com.siyeh.ig.finalization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class FinalizeInspection extends MethodInspection {
    public String getID(){
        return "FinalizeDeclaration";
    }

    public String getDisplayName() {
        return "'finalize()' declaration";
    }

    public String getGroupDisplayName() {
        return GroupNames.FINALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() declared #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new FinalizeDeclaredVisitor(this, inspectionManager, onTheFly);
    }

    private static class FinalizeDeclaredVisitor extends BaseInspectionVisitor {
        private FinalizeDeclaredVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
            if (!"finalize".equals(methodName)) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParameters().length != 0) {
                return;
            }
            registerMethodError(method);
        }
    }

}
