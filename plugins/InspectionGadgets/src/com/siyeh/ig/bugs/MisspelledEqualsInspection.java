package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;

public class MisspelledEqualsInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix("equals");

    public String getDisplayName() {
        return "'equal()' instead of 'equals()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() method should probably be equals() #loc";
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return false;
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
            if (!"equal".equals(methodName)) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParameters().length != 1) {
                return;
            }
            registerMethodError(method);
        }

    }

}
