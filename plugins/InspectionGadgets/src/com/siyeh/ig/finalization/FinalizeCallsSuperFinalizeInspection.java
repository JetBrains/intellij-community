package com.siyeh.ig.finalization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class FinalizeCallsSuperFinalizeInspection extends MethodInspection {
    public String getID(){
        return "FinalizeDoesntCallSuperFinalize";
    }
    public String getDisplayName() {
        return "'finalize()' doesn't call 'super.finalize()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.FINALIZATION_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        return "#ref() doesn't call super.finalize()";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NoExplicitFinalizeCallsVisitor(this, inspectionManager, onTheFly);
    }

    private static class NoExplicitFinalizeCallsVisitor extends BaseInspectionVisitor {
        private NoExplicitFinalizeCallsVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
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
            final CallToSuperFinalizeVisitor visitor = new CallToSuperFinalizeVisitor();
            method.accept(visitor);
            if (visitor.isCallToSuperFinalizeFound()) {
                return;
            }
            registerMethodError(method);
        }

    }

}
