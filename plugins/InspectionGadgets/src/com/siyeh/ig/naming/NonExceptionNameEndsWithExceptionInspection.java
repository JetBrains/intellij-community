package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;

public class NonExceptionNameEndsWithExceptionInspection extends ClassInspection {
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Non-exception class name ends with 'Exception'";
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-exception class name '#ref' ends with 'Exception' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NonExceptionNameEndsWithExceptionVisitor(this, inspectionManager, onTheFly);
    }

    private static class NonExceptionNameEndsWithExceptionVisitor extends BaseInspectionVisitor {
        private NonExceptionNameEndsWithExceptionVisitor(BaseInspection inspection,
                                                         InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down into inner classes
            final String className = aClass.getName();
            if (className == null) {
                return;
            }
            if (!className.endsWith("Exception")) {
                return;
            }
            if (ClassUtils.isSubclass(aClass, "java.lang.Exception")) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
