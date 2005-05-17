package com.siyeh.ipp.concatenation;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class JoinConcatenatedStringLiteralsIntention extends Intention{
    protected PsiElementPredicate getElementPredicate(){
        return new StringConcatPredicate();
    }

    public String getText(){
        return "Join concatenated string literals";
    }

    public String getFamilyName(){
        return "Join Concatenated String Literals";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) element.getParent();
        final PsiExpression lOperand = binaryExpression.getLOperand();
        final PsiLiteralExpression leftLiteral =
                getLeftLiteralOperand(lOperand);
        final PsiLiteralExpression rightLiteral =
                (PsiLiteralExpression) binaryExpression.getROperand();
        String leftText = leftLiteral.getText();
        if(leftText.charAt(0) == '"'){
            leftText = leftText.substring(0, leftText.length() - 1);
        } else{
            leftText = '"' + leftLiteral.getText();
        }
        String rightText = rightLiteral.getText();
        if(rightText.charAt(0) == '"'){
            rightText = rightText.substring(1);
        } else{
            rightText += '"';
        }
        final String newExpression;
        if(lOperand instanceof PsiBinaryExpression){
            final PsiBinaryExpression lBinaryExpression =
                    (PsiBinaryExpression) lOperand;
            newExpression = lBinaryExpression.getLOperand().getText() + " + " +
                    leftText + rightText;
        } else{
            newExpression = leftText + rightText;
        }
        replaceExpression(newExpression, binaryExpression);
    }

    private PsiLiteralExpression getLeftLiteralOperand(PsiExpression expression){
        if(expression instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
            return getLeftLiteralOperand(binaryExpression.getROperand());
        }
        return (PsiLiteralExpression) expression;
    }
}
