package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.TypeUtils;

public class BooleanConstructorInspection extends ExpressionInspection {
    private final BooleanConstructorFix fix = new BooleanConstructorFix();

    public String getDisplayName() {
        return "Boolean constructor call";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Boolean constructor call #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new BooleanConstructorVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class BooleanConstructorFix extends InspectionGadgetsFix {
        public String getName() {
            return "Simplify";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiNewExpression expression = (PsiNewExpression) descriptor.getPsiElement();
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            final String text = args[0].getText();
            final String newExpression;
            if ("true".equals(text)) {
                newExpression = "Boolean.TRUE";
            } else if ("false".equals(text)) {
                newExpression = "Boolean.FALSE";
            } else {
                newExpression = "Boolean.valueOf(" + text + ')';
            }
            replaceExpression(project, expression, newExpression);
        }
    }

    private static class BooleanConstructorVisitor extends BaseInspectionVisitor {
        private BooleanConstructorVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if (!TypeUtils.typeEquals("java.lang.Boolean", type)) {
                return;
            }
            registerError(expression);
        }
    }

}
