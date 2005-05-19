package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
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

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiConditionalExpression expression =
                    (PsiConditionalExpression) descriptor.getPsiElement();

            final PsiExpression thenExpression = expression.getThenExpression();
            final String bodyText = thenExpression.getText();
            replaceExpression(expression, bodyText);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ConditionalExpressionWithIdenticalBranchesVisitor();
    }

    private static class ConditionalExpressionWithIdenticalBranchesVisitor extends BaseInspectionVisitor{


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