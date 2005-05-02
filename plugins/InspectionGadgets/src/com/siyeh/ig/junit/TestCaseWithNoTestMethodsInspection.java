package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

public class TestCaseWithNoTestMethodsInspection extends ClassInspection {
    public String getID(){
        return "JUnitTestCaseWithNoTests";
    }

    public String getDisplayName() {
        return "JUnit test case with no tests";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "JUnit test case #ref has no tests";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TestCaseWithNoTestMethodsVisitor(this, inspectionManager, onTheFly);
    }

    private static class TestCaseWithNoTestMethodsVisitor extends BaseInspectionVisitor {
        private TestCaseWithNoTestMethodsVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            super.visitClass(aClass);
            if (aClass.isInterface()
                    || aClass.isEnum()
                    || aClass.isAnnotationType()
                    || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for(final PsiMethod method : methods){
                if(isTest(method)){
                    return;
                }
            }
            if(!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")){
                return;
            }
            registerClassError(aClass);
        }

        private boolean isTest(PsiMethod method) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return false;
            }
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                return false;
            }

            final PsiType returnType = method.getReturnType();
            if (!PsiType.VOID.equals(returnType)) {
                return false;
            }
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters.length != 0){
                return false;
            }
            final String name = method.getName();
            return name.startsWith("test");

        }

    }

}
