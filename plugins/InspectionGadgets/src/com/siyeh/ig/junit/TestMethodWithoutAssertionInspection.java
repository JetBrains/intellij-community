package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

public class TestMethodWithoutAssertionInspection extends ExpressionInspection {
    public String getID(){
        return "JUnitTestMethodWithNoAssertions";
    }
    public String getDisplayName() {
        return "JUnit test method without any assertions";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "JUnit test method #ref() contains no assertions #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TestMethodWithoutAssertionVisitor(this, inspectionManager, onTheFly);
    }

    private static class TestMethodWithoutAssertionVisitor extends BaseInspectionVisitor {

        private TestMethodWithoutAssertionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            final String methodName = method.getName();
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (!methodName.startsWith("test")) {
                return;
            }
            final PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null) {
                return;
            }
            if (parameters.length != 0
                    ||! returnType.equals(PsiType.VOID)
                    || !method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            final PsiClass targetClass = method.getContainingClass();
            if (!ClassUtils.isSubclass(targetClass, "junit.framework.TestCase")) {
                return;
            }
            final ContainsAssertionVisitor visitor = new ContainsAssertionVisitor();
            method.accept(visitor);
            if (visitor.containsAssertion()) {
                return;
            }
            registerMethodError(method);
        }



    }

}
