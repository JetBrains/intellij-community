package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

import java.util.HashSet;
import java.util.Set;

public class AssertsWithoutMessagesInspection extends ExpressionInspection {

    private static final Set s_assertMethods = new HashSet(10);

    static {
        s_assertMethods.add("assertTrue");
        s_assertMethods.add("assertFalse");
        s_assertMethods.add("assertEquals");
        s_assertMethods.add("assertNull");
        s_assertMethods.add("assertNotNull");
        s_assertMethods.add("assertSame");
        s_assertMethods.add("assertNotSame");
    }

    public String getID(){
        return "MessageMissingOnJUnitAssertion";
    }

    public String getDisplayName() {
        return "Message missing on JUnit assertion";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "JUnit #ref() without message #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AssertionsWithoutMessagesVisitor(this, inspectionManager, onTheFly);
    }

    private static class AssertionsWithoutMessagesVisitor extends BaseInspectionVisitor {

        private AssertionsWithoutMessagesVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isJUnitAssertion(expression)) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();

            final PsiMethod method = (PsiMethod) methodExpression.resolve();
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length < 2) {
                registerMethodCallError(expression);
                return;
            }
            final PsiManager psiManager = expression.getManager();

            final Project project = psiManager.getProject();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType = PsiType.getJavaLangString(psiManager, scope);
            final PsiType paramType1 = parameters[0].getType();
            if (paramType1.equals(stringType)) {
                if (parameters.length == 2) {
                    final PsiType paramType2 = parameters[1].getType();
                    if (paramType2.equals(stringType)) {
                        registerMethodCallError(expression);
                    }
                }
            } else {
                registerMethodCallError(expression);
            }
        }

        private static boolean isJUnitAssertion(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!s_assertMethods.contains(methodName)) {
                return false;
            }
            final PsiMethod method = (PsiMethod) methodExpression.resolve();
            if (method == null) {
                return false;
            }

            final PsiClass targetClass = method.getContainingClass();
            return ClassUtils.isSubclass(targetClass, "junit.framework.Assert");
        }

    }

}
