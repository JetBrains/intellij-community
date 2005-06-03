package com.siyeh.ipp.chartostring;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class StringToCharPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiLiteralExpression)){
            return false;
        }
        final PsiLiteralExpression expression =
                (PsiLiteralExpression) element;
        final PsiType type = expression.getType();
        final String typeText = type.getCanonicalText();
        if(!"java.lang.String".equals(typeText)){
            return false;
        }
        final String value = (String) expression.getValue();

        if(value == null || value.length() != 1){
            return false;
        }
        return isInConcatenationContext(element);
    }

    private boolean isInConcatenationContext(PsiElement element){
        final PsiElement parent = element.getParent();
        if(parent instanceof PsiBinaryExpression){
            final PsiBinaryExpression parentExpression =
                    (PsiBinaryExpression) parent;
            final PsiType parentType = parentExpression.getType();
            if(parentType == null){
                return false;
            }
            final String parentTypeText = parentType.getCanonicalText();
            if(!"java.lang.String".equals(parentTypeText)){
                return false;
            }
            final PsiExpression otherOperand;
            final PsiExpression lhs = parentExpression.getLOperand();
            final PsiExpression rhs = parentExpression.getROperand();
            if(rhs == null){
                return false;
            }
            if(lhs.equals(element)){
                otherOperand = rhs;
            } else{
                otherOperand = lhs;
            }
            final PsiType otherOperandType = otherOperand.getType();
            if(otherOperandType == null){
                return false;
            }
            final String otherOperandTypeText =
                    otherOperandType.getCanonicalText();
            return "java.lang.String".equals(otherOperandTypeText);
        } else if(parent instanceof PsiAssignmentExpression){
            final PsiAssignmentExpression parentExpression =
                    (PsiAssignmentExpression) parent;
            final PsiJavaToken sign = parentExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!JavaTokenType.PLUSEQ.equals(tokenType)){
                return false;
            }
            final PsiType parentType = parentExpression.getType();
            if(parentType == null){
                return false;
            }
            final String parentTypeText = parentType.getCanonicalText();
            return "java.lang.String".equals(parentTypeText);
        }
        if(parent instanceof PsiExpressionList &&
                parent.getParent() instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression methodCall =
                    (PsiMethodCallExpression) parent.getParent();
            final PsiReferenceExpression methodExpression =
                    methodCall.getMethodExpression();
            final PsiType type = methodExpression.getType();
            if(type == null){
                return false;
            }
            final String className = type.getCanonicalText();
            if(!"java.lang.StringBuffer".equals(className) &&
                    !"java.lang.StringBuilder".equals(className)){
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            return "append".equals(methodName);
        } else{
            return false;
        }
    }
}
