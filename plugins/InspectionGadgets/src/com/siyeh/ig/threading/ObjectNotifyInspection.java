package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class ObjectNotifyInspection extends ExpressionInspection {
    private final ObjectNotifyFix fix = new ObjectNotifyFix();

    public String getDisplayName() {
        return "Call to notify() instead of notifyAll()";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref should probably be replaced by notifyAll() #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ObjectNotifyVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ObjectNotifyFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with notifyAll()";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement methodNameElement = descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression =
                    (PsiReferenceExpression) methodNameElement.getParent();
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                replaceExpression(project, methodExpression, "notifyAll");
            } else {
                final String qualifierText = qualifier.getText();
                replaceExpression(project, methodExpression, qualifierText + ".notifyAll");
            }
        }

    }

    private static class ObjectNotifyVisitor extends BaseInspectionVisitor {
        private ObjectNotifyVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();

            if (!"notify".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            if (argumentList.getExpressions().length != 0) {
                return;
            }
            registerMethodCallError(expression);
        }
    }

}
