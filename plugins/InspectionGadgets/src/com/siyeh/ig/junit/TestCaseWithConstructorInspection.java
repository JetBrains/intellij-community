package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

public class TestCaseWithConstructorInspection extends ClassInspection {
    public String getID(){
        return "JUnitTestCaseWithNonTrivialConstructors";
    }

    public String getDisplayName() {
        return "JUnit TestCase with non-trivial constructors";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Initialization logic in constructor #ref() instead of setUp()";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TestCaseWithConstructorVisitor(this, inspectionManager, onTheFly);
    }

    private static class TestCaseWithConstructorVisitor extends BaseInspectionVisitor {

        private TestCaseWithConstructorVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            // note: no call to super
            if (!method.isConstructor()) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null)
            {
                return;
            }
            if (isTrivial(method)) {
                return;
            }
            if(!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")){
                return;
            }
            registerMethodError(method);
        }

        private static boolean isTrivial(PsiMethod method) {
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return true;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                return true;
            }
            if (statements.length > 1) {
                return false;
            }
            final PsiStatement statement = statements[0];
            if (!(statement instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpression expression =
                    ((PsiExpressionStatement) statement).getExpression();
            if (expression == null) {
                return false;
            }
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) expression;
            final PsiReferenceExpression ref = call.getMethodExpression();
            if (ref == null) {
                return false;
            }
            final String text = ref.getText();
            return "super".equals(text);
        }

    }

}
