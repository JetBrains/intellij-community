package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class StringToStringInspection extends ExpressionInspection {
    private final StringToStringFix fix = new StringToStringFix();

    public String getDisplayName() {
        return "Redundant String.toString()";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref is redundant #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StringToStringVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class StringToStringFix extends InspectionGadgetsFix {
        public String getName() {
            return "Simplify";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiMethodCallExpression call = (PsiMethodCallExpression) descriptor.getPsiElement();
            final PsiReferenceExpression expression = call.getMethodExpression();
            final PsiExpression qualifier = expression.getQualifierExpression();
            final String qualifierText = qualifier.getText();
            replaceExpression(project, call, qualifierText);
        }
    }

    private static class StringToStringVisitor extends BaseInspectionVisitor {
        private StringToStringVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"toString".equals(methodName)) {
                return;
            }

            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 0) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            final String className = aClass.getQualifiedName();
            if (!"java.lang.String".equals(className)) {
                return;
            }
            registerError(expression);
        }
    }

}
