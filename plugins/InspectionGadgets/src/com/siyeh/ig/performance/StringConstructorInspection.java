package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.TypeUtils;

public class StringConstructorInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Redundant String constructor call";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref is redundant #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StringConstructorVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new StringConstructorFix((PsiNewExpression) location);
    }

    private static class StringConstructorFix extends InspectionGadgetsFix {
        private final String m_name;

        private StringConstructorFix(PsiNewExpression expression) {
            super();
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if (args.length == 1) {
                m_name = "Replace with arg";
            } else {
                m_name = "Replace with empty string";
            }
        }

        public String getName() {
            return m_name;
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiNewExpression expression = (PsiNewExpression) descriptor.getPsiElement();
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            final String argText;
            if (args.length == 1) {
                argText = args[0].getText();
            } else {
                argText = "\"\"";
            }
            replaceExpression(project, expression, argText);
        }
    }

    private static class StringConstructorVisitor extends BaseInspectionVisitor {
        private StringConstructorVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            if (argList == null) {
                return;
            }
            final PsiExpression[] args = argList.getExpressions();

            if (args.length > 1) {
                return;
            }
            if (args.length == 1) {
                final PsiType parameterType = args[0].getType();
                if (!TypeUtils.isJavaLangString(parameterType)) {
                    return;
                }
            }
            registerError(expression);
        }
    }

}
