package com.siyeh.ig.methodmetrics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceList;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

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
        assert method != null;
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

    public BaseInspectionVisitor buildVisitor() {
        return new ParametersPerMethodVisitor();
    }

    private class ParametersPerMethodVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
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
