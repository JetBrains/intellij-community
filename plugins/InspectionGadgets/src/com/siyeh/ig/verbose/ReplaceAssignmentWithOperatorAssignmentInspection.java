package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ExpressionEquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.WellFormednessUtils;

public class ReplaceAssignmentWithOperatorAssignmentInspection
        extends ExpressionInspection{
    public String getID(){
        return "AssignmentReplaceableWithOperatorAssignment";
    }

    public String getDisplayName(){
        return "Assignment replaceable with operator assignment";
    }

    public String getGroupDisplayName(){
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref could be simplified to " +
                       calculateReplacementExpression(
                               (PsiAssignmentExpression) location) +
                       " #loc";
    }

    private static String calculateReplacementExpression(
            PsiAssignmentExpression expression){
        final PsiBinaryExpression rhs =
                (PsiBinaryExpression) expression.getRExpression();
        final PsiExpression lhs = expression.getLExpression();
        final PsiJavaToken sign = rhs.getOperationSign();
        final PsiExpression rhsRhs = rhs.getROperand();
        String signText = sign.getText();
        if("&&".equals(signText)){
            signText = "&";
        } else if("||".equals(signText)){
            signText = "|";
        }
        return lhs.getText() + ' ' + signText + "= " + rhsRhs.getText();
    }

    public BaseInspectionVisitor createVisitor(
            InspectionManager inspectionManager, boolean onTheFly){
        return new ReplaceAssignmentWithOperatorAssignmentVisitor(this,
                                                                  inspectionManager,
                                                                  onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new ReplaceAssignmentWithOperatorAssignmentFix(
                (PsiAssignmentExpression) location);
    }

    private static class ReplaceAssignmentWithOperatorAssignmentFix
            extends InspectionGadgetsFix{
        private final String m_name;

        private ReplaceAssignmentWithOperatorAssignmentFix(
                PsiAssignmentExpression expression){
            super();
            final PsiBinaryExpression rhs =
                    (PsiBinaryExpression) expression.getRExpression();
            final PsiJavaToken sign = rhs.getOperationSign();
            String signText = sign.getText();
            if("&&".equals(signText)){
                signText = "&";
            } else if("||".equals(signText)){
                signText = "|";
            }
            m_name = "Replace = with " + signText + '=';
        }

        public String getName(){
            return m_name;
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)){
                return;
            }
            final PsiAssignmentExpression expression =
                    (PsiAssignmentExpression) descriptor.getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            replaceExpression(project, expression, newExpression);
        }
    }

    private static class ReplaceAssignmentWithOperatorAssignmentVisitor
            extends BaseInspectionVisitor{
        private ReplaceAssignmentWithOperatorAssignmentVisitor(
                BaseInspection inspection,
                InspectionManager inspectionManager, boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssignmentExpression(
                PsiAssignmentExpression assignment){
            super.visitAssignmentExpression(assignment);
            if(!WellFormednessUtils.isWellFormed(assignment)){
                return;
            }
            final PsiJavaToken sign = assignment.getOperationSign();
            final IElementType assignmentTokenType = sign.getTokenType();
            if(!assignmentTokenType.equals(JavaTokenType.EQ)){
                return;
            }
            final PsiExpression lhs = assignment.getLExpression();
            final PsiExpression rhs = assignment.getRExpression();

            if(!(rhs instanceof PsiBinaryExpression)){
                return;
            }
            final PsiBinaryExpression binaryRhs = (PsiBinaryExpression) rhs;
            if(!WellFormednessUtils.isWellFormed(binaryRhs)){
                return;
            }
            final PsiExpression lOperand = binaryRhs.getLOperand();
            if(SideEffectChecker.mayHaveSideEffects(lhs)){
                return;
            }
            if(!ExpressionEquivalenceChecker.expressionsAreEquivalent(lhs,
                                                                      lOperand)){
                return;
            }
            registerError(assignment);
        }
    }
}
