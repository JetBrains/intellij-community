package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

public class CastConflictsWithInstanceofInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "Cast conflicts with 'instanceof'";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Cast to #ref conflicts with surrounding 'instanceof' check #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new CastConflictsWithInstanceofVisitor();
    }

    private static class CastConflictsWithInstanceofVisitor
            extends BaseInspectionVisitor{
        public void visitTypeCastExpression(
                @NotNull PsiTypeCastExpression expression){
            super.visitTypeCastExpression(expression);

            final PsiTypeElement castTypeElement = expression.getCastType();
            if(castTypeElement == null){
                return;
            }
            final PsiType castType = expression.getType();
            if(castType == null){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(operand == null){
                return;
            }
            if(!(operand instanceof PsiReferenceExpression)){
                return;
            }
            boolean hasConflictingInstanceof = false;
            boolean hasConfirmingInstanceof = false;
            PsiIfStatement currentStatement = PsiTreeUtil
                    .getParentOfType(expression, PsiIfStatement.class);
            while(currentStatement != null){
                if(!isInElse(expression, currentStatement)){
                    final PsiExpression condition = currentStatement
                            .getCondition();
                    if(condition instanceof PsiInstanceOfExpression){
                        final PsiInstanceOfExpression instanceOfCondition =
                                (PsiInstanceOfExpression) condition;
                        if(isConflicting(instanceOfCondition, operand,
                                         castType)){
                            hasConflictingInstanceof = true;
                        } else if(isConfirming(instanceOfCondition, operand,
                                               castType)){
                            hasConfirmingInstanceof = true;
                        }
                    }
                }
                currentStatement = PsiTreeUtil
                        .getParentOfType(currentStatement,
                                         PsiIfStatement.class);
            }
            if(hasConflictingInstanceof && !hasConfirmingInstanceof){
                registerError(castTypeElement);
            }
        }

        private static boolean isInElse(PsiExpression expression,
                                 PsiIfStatement statement){
            final PsiStatement branch = statement.getElseBranch();
            if(branch == null)
            {
                return false;
            }
            return PsiTreeUtil.isAncestor(branch, expression, true);
        }

        private boolean isConflicting(PsiInstanceOfExpression condition,
                                      PsiExpression operand,
                                      PsiType castType){
            final PsiExpression conditionOperand = condition.getOperand();
            if(!EquivalenceChecker
                    .expressionsAreEquivalent(operand, conditionOperand)){
                return false;
            }
            final PsiTypeElement typeElement = condition.getCheckType();
            if(typeElement == null){
                return false;
            }
            final PsiType type = typeElement.getType();
            if(type == null){
                return false;
            }
            return !type
                    .equals(castType);   //TODO: should this take inheritance into account
        }

        private boolean isConfirming(PsiInstanceOfExpression condition,
                                     PsiExpression operand,
                                     PsiType castType){
            final PsiExpression conditionOperand = condition.getOperand();
            if(!EquivalenceChecker
                    .expressionsAreEquivalent(operand, conditionOperand)){
                return false;
            }

            final PsiTypeElement typeElement = condition.getCheckType();
            if(typeElement == null){
                return false;
            }
            final PsiType type = typeElement.getType();
            if(type == null){
                return false;
            }
            return type
                    .equals(castType);   //TODO: should this take inheritance into account
        }
    }
}
