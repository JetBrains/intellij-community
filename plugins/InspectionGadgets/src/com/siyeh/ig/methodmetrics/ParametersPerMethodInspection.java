package com.siyeh.ig.methodmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.LibraryUtil;

public class ParametersPerMethodInspection extends MethodMetricInspection {

    public String getDisplayName() {
        return "Method with too many parameters";
    }

    public String getGroupDisplayName() {
        return GroupNames.METHODMETRICS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod method = (PsiMethod) location.getParent();
        final PsiParameterList parameterList = method.getParameterList();
        final int numParams = parameterList.getParameters().length;
        return "#ref has too many parameters (num parameters = " + numParams + ") #loc";
    }

    protected int getDefaultLimit() {
        return 5;
    }

    protected String getConfigurationLabel() {
        return "Parameter limit:";
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
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null)
            {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters== null)
            {
                return;
            }
            if (parameters.length <= getLimit()) {
                return;
            }

            final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);
            for (int i = 0; i < superMethods.length; i++) {
                final PsiMethod superMethod = superMethods[i];
                final PsiClass containingClass = superMethod.getContainingClass();
                if (containingClass != null) {
                    if (LibraryUtil.classIsInLibrary(containingClass)) {
                        return;
                    }
                }
            }
            registerMethodError(method);
        }
    }

}
