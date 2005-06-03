package com.siyeh.ig.verbose;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

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
        assert rhs != null;
        final PsiJavaToken sign = rhs.getOperationSign();
        final PsiExpression rhsRhs = rhs.getROperand();
        assert rhsRhs != null;
        String signText = sign.getText();
        if("&&".equals(signText)){
            signText = "&";
        } else if("||".equals(signText)){
            signText = "|";
        }
        return lhs.getText() + ' ' + signText + "= " + rhsRhs.getText();
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ReplaceAssignmentWithOperatorAssignmentVisitor();
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
            assert rhs != null;
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

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiAssignmentExpression expression =
                    (PsiAssignmentExpression) descriptor.getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private static class ReplaceAssignmentWithOperatorAssignmentVisitor
            extends BaseInspectionVisitor{

        public void visitAssignmentExpression(@NotNull
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
            if(!(binaryRhs.getROperand() != null)){
                return;
            }
            final PsiExpression lOperand = binaryRhs.getLOperand();
            if(SideEffectChecker.mayHaveSideEffects(lhs)){
                return;
            }
            if(!EquivalenceChecker.expressionsAreEquivalent(lhs,
                                                                      lOperand)){
                return;
            }
            registerError(assignment);
        }
    }
}
