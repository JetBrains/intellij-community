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

public class NestedMethodCallInspection extends ExpressionInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreFieldInitializations = true;
    private final NestedMethodCallFix fix = new NestedMethodCallFix();

    public String getDisplayName() {
        return "Nested method call";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Nested method call #ref() #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore nested method calls in field initializers",
                this, "m_ignoreFieldInitializations");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NestedMethodCallVisitor(this, inspectionManager, onTheFly);
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    private static class NestedMethodCallFix extends InspectionGadgetsFix {
        public String getName() {
            return "Introduce variable";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final RefactoringActionHandlerFactory factory = RefactoringActionHandlerFactory.getInstance();
            final RefactoringActionHandler introduceHandler =
                    factory.createIntroduceVariableHandler();
            final PsiElement methodNameElement = descriptor.getPsiElement();
            final PsiElement methodExpression = methodNameElement.getParent();
            final PsiElement methodCallExpression = methodExpression.getParent();
            introduceHandler.invoke(project, new PsiElement[]{methodCallExpression}, null);
        }
    }

    private class NestedMethodCallVisitor extends BaseInspectionVisitor {
        private NestedMethodCallVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            PsiExpression outerExpression = expression;
            while(outerExpression.getParent() instanceof PsiExpression){
                outerExpression = (PsiExpression) outerExpression.getParent();
            }
            final PsiElement parent = outerExpression.getParent();
            if(!(parent instanceof PsiExpressionList)){
                return;
            }
            final PsiElement grandParent = parent.getParent();
            if(!(grandParent instanceof PsiCallExpression)){
                return;
            }
            if(grandParent instanceof PsiMethodCallExpression){

                final PsiMethodCallExpression surroundingCall =
                        (PsiMethodCallExpression) grandParent;
                final PsiReferenceExpression methodExpression =
                        surroundingCall.getMethodExpression();
                final String callName = methodExpression.getReferenceName();
                if("this".equals(callName) || "super".equals(callName)){
                    return;     //ignore nested method calls at the start of a constructor,
                    //where they can't be extracted
                }
            }
            final PsiReferenceExpression reference =
                    expression.getMethodExpression();
            if(reference == null){
                return;
            }
            if(m_ignoreFieldInitializations){
                final PsiElement field =
                        PsiTreeUtil.getParentOfType(expression, PsiField.class);
                if(field != null){
                    return;
                }
            }
            registerMethodCallError(expression);
        }
    }
}
