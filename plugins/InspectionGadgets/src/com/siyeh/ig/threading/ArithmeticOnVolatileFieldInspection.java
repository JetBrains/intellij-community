package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.WellFormednessUtils;

public class ArithmeticOnVolatileFieldInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "Arithmetic operation on volatile field";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Arithmetic operation on volatile field '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new AritmeticOnVolatileFieldInspection(this, inspectionManager,
                                                      onTheFly);
    }

    private static class AritmeticOnVolatileFieldInspection
            extends BaseInspectionVisitor{
        private AritmeticOnVolatileFieldInspection(BaseInspection inspection,
                                                   InspectionManager inspectionManager,
                                                   boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!JavaTokenType.ASTERISK.equals(tokenType) &&
                       !JavaTokenType.DIV.equals(tokenType) &&
                       !JavaTokenType.PLUS.equals(tokenType) &&
                       !JavaTokenType.MINUS.equals(tokenType) &&
                       !JavaTokenType.PERC.equals(tokenType)){
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            checkForVolatile(lhs);
            final PsiExpression rhs = expression.getROperand();
            checkForVolatile(rhs);
        }
        public void visitAssignmentExpression(PsiAssignmentExpression expression){
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!JavaTokenType.ASTERISKEQ.equals(tokenType) &&
                       !JavaTokenType.DIVEQ.equals(tokenType) &&
                       !JavaTokenType.PLUSEQ.equals(tokenType) &&
                       !JavaTokenType.MINUSEQ.equals(tokenType) &&
                       !JavaTokenType.PERCEQ.equals(tokenType)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            checkForVolatile(lhs);
            final PsiExpression rhs = expression.getRExpression();
            checkForVolatile(rhs);
        }

        private void checkForVolatile(PsiExpression expression){
            if(!(expression instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression reference = (PsiReferenceExpression) expression;
            final PsiElement referent = reference.resolve();
            if(!(referent instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) referent;
            if(field.hasModifierProperty(PsiModifier.VOLATILE))
            {
                registerError(expression);
            }
        }
    }
}
