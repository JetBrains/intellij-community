package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;

public class SetupCallsSuperSetupInspection extends MethodInspection {

    public String getDisplayName() {
        return "setUp() doesn't call super.setUp()";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() doesn't call super.setUp()";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SetupCallsSuperSetupVisitor(this, inspectionManager, onTheFly);
    }

    private static class SetupCallsSuperSetupVisitor extends BaseInspectionVisitor {
        private SetupCallsSuperSetupVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
            if (!"setUp".equals(methodName)) {
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
            final CallToSuperSetupVisitor visitor = new CallToSuperSetupVisitor();
            method.accept(visitor);
            if (visitor.isCallToSuperSetupFound()) {
                return;
            }
            registerMethodError(method);
        }

    }

}
