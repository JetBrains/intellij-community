package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;

public class TeardownCallsSuperTeardownInspection extends MethodInspection {

    public String getDisplayName() {
        return "tearDown() doesn't call super.tearDown()";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() doesn't call super.tearDown()";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TeardownCallsSuperTeardownVisitor(this, inspectionManager, onTheFly);
    }

    private static class TeardownCallsSuperTeardownVisitor extends BaseInspectionVisitor {
        private TeardownCallsSuperTeardownVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
            if (!"tearDown".equals(methodName)) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return;
            }
            if (parameterList.getParameters().length != 0) {
                return;
            }

            final PsiClass targetClass = method.getContainingClass();
            if (!ClassUtils.isSubclass(targetClass, "junit.framework.TestCase")) {
                return;
            }
            final CallToSuperTeardownVisitor visitor = new CallToSuperTeardownVisitor();
            method.accept(visitor);
            if (visitor.isCallToSuperTeardownFound()) {
                return;
            }
            registerMethodError(method);
        }

    }

}
