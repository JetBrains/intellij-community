package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;

public class TestMethodIsPublicVoidNoArgInspection extends MethodInspection {
    public String getID(){
        return "TestMethodWithIncorrectSignature";
    }
    public String getDisplayName() {
        return "Test method with incorrect signature";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() is not declared 'public void " + location.getText() + "()";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TestMethodIsPublicVoidNoArgVisitor(this, inspectionManager, onTheFly);
    }

    private static class TestMethodIsPublicVoidNoArgVisitor extends BaseInspectionVisitor {
        private TestMethodIsPublicVoidNoArgVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
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
            if (parameters.length == 0
                    && returnType.equals(PsiType.VOID)
                    && method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            final PsiClass targetClass = method.getContainingClass();
            if (!ClassUtils.isSubclass(targetClass, "junit.framework.TestCase")) {
                return;
            }
            registerMethodError(method);
        }

    }

}
