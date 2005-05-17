package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NotNull;

public class ParametersPerMethodInspection extends MethodMetricInspection {
    public String getID(){
        return "MethodWithTooManyParameters";
    }
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

    public BaseInspectionVisitor buildVisitor() {
        return new ParametersPerMethodVisitor();
    }

    private class ParametersPerMethodVisitor extends BaseInspectionVisitor {
       
        public void visitMethod(@NotNull PsiMethod method) {
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
            for(final PsiMethod superMethod : superMethods){
                final PsiClass containingClass = superMethod.getContainingClass();
                if(containingClass != null){
                    if(LibraryUtil.classIsInLibrary(containingClass)){
                        return;
                    }
                }
            }
            registerMethodError(method);
        }
    }

}
