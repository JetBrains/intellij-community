package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class SubtractionInCompareToInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "Subtraction in compareTo()";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Subtraction (#ref) in compareTo() may result in overflow errors #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new SubtractionInCompareToVisitor();
    }

    private static class SubtractionInCompareToVisitor
                                                       extends BaseInspectionVisitor{
        public void visitBinaryExpression(@NotNull PsiBinaryExpression exp){
            super.visitBinaryExpression(exp);
            if(!(exp.getROperand() != null)){
                return;
            }
            if(!isSubtraction(exp)){
                return;
            }
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(exp, PsiMethod.class);
            if(!MethodUtils.isCompareTo(method)){
                return;
            }
            registerError(exp);
        }


        private static boolean isSubtraction(PsiBinaryExpression exp){
            final PsiExpression lhs = exp.getLOperand();
            final PsiExpression rhs = exp.getROperand();
            if(rhs == null){
                return false;
            }
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            return tokenType.equals(JavaTokenType.MINUS);
        }
    }

}
