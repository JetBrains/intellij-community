package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.EquivalenceChecker;

public class ConditionalExpressionWithIdenticalBranchesInspection extends ExpressionInspection{
    private InspectionGadgetsFix fix = new CollapseConditional();

    public String getDisplayName(){
        return "Conditional expression with identical branches";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Conditional expression #ref with identical branches #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class CollapseConditional extends InspectionGadgetsFix{
        public String getName(){
            return "Collapse conditional expression";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            final PsiConditionalExpression expression =
                    (PsiConditionalExpression) descriptor.getPsiElement();

            final PsiExpression thenExpression = expression.getThenExpression();
            final String bodyText = thenExpression.getText();
            replaceExpression(expression, bodyText);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new ConditionalExpressionWithIdenticalBranchesVisitor(this, inspectionManager, onTheFly);
    }

    private static class ConditionalExpressionWithIdenticalBranchesVisitor extends BaseInspectionVisitor{
        private ConditionalExpressionWithIdenticalBranchesVisitor(BaseInspection inspection,
                                   InspectionManager inspectionManager,
                                   boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitConditionalExpression(PsiConditionalExpression expression){
            super.visitConditionalExpression(expression);
            final PsiExpression thenExpression = expression.getThenExpression();
            final PsiExpression elseExpression = expression.getElseExpression();
            if(EquivalenceChecker.expressionsAreEquivalent(thenExpression, elseExpression))
            {
                registerError(expression);
            }
        }

    }
}