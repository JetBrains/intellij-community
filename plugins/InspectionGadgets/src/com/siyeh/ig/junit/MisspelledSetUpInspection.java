package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class MisspelledSetUpInspection extends MethodInspection {

    public String getDisplayName() {
        return "'setup()' instead of 'setUp()'";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new RenameFix("setUp");
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() probably be setUp() #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MisspelledSetUpVisitor(this, inspectionManager, onTheFly);
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    private static class MisspelledSetUpVisitor extends BaseInspectionVisitor {

        private MisspelledSetUpVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super
            final PsiClass aClass = method.getContainingClass();
            final String methodName = method.getName();
            if(!"setup".equals(methodName)){
                return;
            }
            if (aClass == null) {
                return;
            }
            if (!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")) {
                return;
            }

            registerMethodError(method);
        }


    }

}
