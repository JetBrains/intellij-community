package com.siyeh.ig.methodmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.LibraryUtil;

public class ThrownExceptionsPerMethodInspection extends MethodMetricInspection {
    public String getID(){
        return "MethodWithTooExceptionsDeclared";
    }
    public String getDisplayName() {
        return "Method with too many exceptions declared";
    }

    public String getGroupDisplayName() {
        return GroupNames.METHODMETRICS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod method = (PsiMethod) location.getParent();
        final PsiReferenceList throwsList = method.getThrowsList();
        final int numThrows = throwsList.getReferenceElements().length;
        return "#ref has too many exceptions declared (num exceptions = " + numThrows + ") #loc";
    }

    protected int getDefaultLimit() {
        return 3;
    }

    protected String getConfigurationLabel() {
        return "Exceptions thrown limit:";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ParametersPerMethodVisitor(this, inspectionManager, onTheFly);
    }

    private class ParametersPerMethodVisitor extends BaseInspectionVisitor {
        private ParametersPerMethodVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            // note: no call to super
            final PsiReferenceList throwList = method.getThrowsList();
            if(throwList == null)
            {
                return;
            }
            final PsiJavaCodeReferenceElement[] thrownExceptions = throwList.getReferenceElements();
            if(thrownExceptions== null)
            {
                return;
            }
            if (thrownExceptions.length <= getLimit()) {
                return;
            }
            registerMethodError(method);
        }
    }

}
