package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MoveClassFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TestUtils;

public class TestCaseInProductCodeInspection extends ClassInspection {
    private final MoveClassFix fix = new MoveClassFix();

    public String getID(){
        return "JUnitTestCaseInProductSource";
    }

    public String getDisplayName() {
        return "JUnit TestCase in product source";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Test case #ref should probably be placed in a test source tree #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TestCaseInProductCodeVisitor(this, inspectionManager, onTheFly);
    }

    private static class TestCaseInProductCodeVisitor extends BaseInspectionVisitor {

        private TestCaseInProductCodeVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            if(TestUtils.isTest(aClass))
            {
                return;
            }
            if (!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")) {
                return;
            }
            registerClassError(aClass);
        }
    }
}
