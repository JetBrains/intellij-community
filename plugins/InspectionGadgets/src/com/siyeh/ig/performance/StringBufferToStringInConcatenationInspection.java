package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class StringBufferToStringInConcatenationInspection extends ExpressionInspection {
    private final StringBufferToStringFix fix = new StringBufferToStringFix();

    public String getDisplayName() {
        return "StringBuffer.toString() in concatenation";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Calls to StringBuffer.#ref() in concatenation #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StringBufferToStringVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class StringBufferToStringFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove .toString()";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement methodNameToken = descriptor.getPsiElement();
            final PsiElement methodCallExpression = methodNameToken.getParent();
            final PsiMethodCallExpression methodCall =
                    (PsiMethodCallExpression) methodCallExpression.getParent();
            final PsiReferenceExpression expression = methodCall.getMethodExpression();
            final PsiExpression qualifier = expression.getQualifierExpression();
            final String newExpression = qualifier.getText();
            replaceExpression(project, methodCall, newExpression);
        }
    }

    private static class StringBufferToStringVisitor extends BaseInspectionVisitor {
        private StringBufferToStringVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression parentBinary = (PsiBinaryExpression) parent;
            final PsiJavaToken sign = parentBinary.getOperationSign();
            if (sign == null) {
                return;
            }
            if (!(sign.getTokenType() == JavaTokenType.PLUS)) {
                return;
            }
            final PsiExpression rhs = parentBinary.getROperand();
            if (rhs == null) {
                return;
            }
            if (!rhs.equals(expression)) {
                return;
            }
            if (!isStringBufferToString(expression)) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isStringBufferToString(PsiMethodCallExpression expression) {
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            final String methodName = method.getName();
            if (methodName == null) {
                return false;
            }
            if (!"toString".equals(methodName)) {
                return false;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return false;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length != 0) {
                return false;
            }
            final PsiClass aClass = method.getContainingClass();
            final String className = aClass.getQualifiedName();
            if (!"java.lang.StringBuffer".equals(className)) {
                return false;
            }
            return true;
        }
    }

}
