package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class UnnecessarySuperConstructorInspection extends ExpressionInspection {
    private final UnnecessarySuperConstructorFix fix = new UnnecessarySuperConstructorFix();

    public String getDisplayName() {
        return "Unnecessary 'super()' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref is unnecessary #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessarySuperConstructorVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnnecessarySuperConstructorFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unnecessary super()";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiElement superCall = descriptor.getPsiElement();
            final PsiElement callStatement = superCall.getParent();
            deleteElement(callStatement);
        }

    }

    private static class UnnecessarySuperConstructorVisitor extends BaseInspectionVisitor {
        private UnnecessarySuperConstructorVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodText = methodExpression.getText();
            if (!"super".equals(methodText)) {
                return;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 0) {
                return;
            }
            registerError(call);
        }
    }
}
