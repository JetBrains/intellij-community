package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;

public class MisspelledToStringInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix("toString");

    public String getDisplayName() {
        return "'tostring()' instead of 'toString()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return false;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() method should probably be toString() #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MisspelledToStringVisitor(this, inspectionManager, onTheFly);
    }

    private static class MisspelledToStringVisitor extends BaseInspectionVisitor {
        private MisspelledToStringVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //note: no call to super
            final String methodName = method.getName();
            if (!"tostring".equals(methodName)) {
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
