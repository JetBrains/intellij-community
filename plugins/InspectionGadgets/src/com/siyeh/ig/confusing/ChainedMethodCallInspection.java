package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.*;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class ChainedMethodCallInspection extends ExpressionInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreFieldInitializations = true;
    private final ChainedMethodCallFix fix = new ChainedMethodCallFix();

    public String getDisplayName() {
        return "Chained method calls";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Chained method call #ref() #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ChainedMethodCallVisitor(this, inspectionManager, onTheFly);
    }


    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore chained method calls in field initializers",
                this, "m_ignoreFieldInitializations");
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ChainedMethodCallFix extends InspectionGadgetsFix {
        public String getName() {
            return "Introduce variable";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final RefactoringActionHandlerFactory factory =
                    RefactoringActionHandlerFactory.getInstance();
            final RefactoringActionHandler introduceHandler =
                    factory.createIntroduceVariableHandler();
            final PsiElement methodNameElement = descriptor.getPsiElement();
            final PsiReferenceExpression methodCallExpression =
                    (PsiReferenceExpression) methodNameElement.getParent();
            final PsiExpression qualifier = methodCallExpression.getQualifierExpression();
            introduceHandler.invoke(project,
                    new PsiElement[]{qualifier},
                    null);
        }
    }

    private class ChainedMethodCallVisitor extends BaseInspectionVisitor {

        private ChainedMethodCallVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression reference = expression.getMethodExpression();
            if (reference == null) {
                return;
            }
            final PsiExpression qualifier = reference.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!isCallExpression(qualifier)) {
                return;
            }

            if (m_ignoreFieldInitializations) {
                final PsiElement field = PsiTreeUtil.getParentOfType(expression, PsiField.class);
                if (field != null) {
                    return;
                }
            }
            registerMethodCallError(expression);
        }

    }

    private static boolean isCallExpression(PsiExpression expression) {
        if (expression instanceof PsiMethodCallExpression ||
                expression instanceof PsiNewExpression) {
            return true;
        }
        if (expression instanceof PsiParenthesizedExpression) {
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression) expression;
            final PsiExpression containedExpression =
                    parenthesizedExpression.getExpression();
            return isCallExpression(containedExpression);
        }
        return false;
    }

}
