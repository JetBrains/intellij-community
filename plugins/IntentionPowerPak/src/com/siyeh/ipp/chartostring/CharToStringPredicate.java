package com.siyeh.ipp.chartostring;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class CharToStringPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiLiteralExpression)){
            return false;
        }
        final PsiLiteralExpression expression =
                (PsiLiteralExpression) element;
        final PsiType type = expression.getType();
        if(!PsiType.CHAR.equals(type)){
            return false;
        }
        return isInConcatenationContext(expression);
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
            return "java.lang.String".equals(parentTypeText);
        } else if(parent instanceof PsiAssignmentExpression){
            final PsiAssignmentExpression parentExpression =
                    (PsiAssignmentExpression) parent;
            final PsiJavaToken sign = parentExpression.getOperationSign();
            if(sign == null){
                return false;
            }
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
