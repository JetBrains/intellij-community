package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;

public class MisspelledHashcodeInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix("hashCode");

    public String getDisplayName() {
        return "'hashcode()' instead of 'hashCode()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() should probably be hashCode() #loc";
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MisspelledHashcodeVisitor(this, inspectionManager, onTheFly);
    }

    private static class MisspelledHashcodeVisitor extends BaseInspectionVisitor {
        private MisspelledHashcodeVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //note: no call to super
            final String methodName = method.getName();
            if (!"hashcode".equals(methodName)) {
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
