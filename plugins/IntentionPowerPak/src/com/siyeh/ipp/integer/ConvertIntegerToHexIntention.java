package com.siyeh.ipp.integer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

import java.math.BigInteger;

public class ConvertIntegerToHexIntention extends Intention{
    public String getText(){
        return "Convert to hex";
    }

    public String getFamilyName(){
        return "Convert To Hexadecimal";
    }

    public PsiElementPredicate getElementPredicate(){
        return new ConvertIntegerToHexPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiLiteralExpression exp = (PsiLiteralExpression) element;
        String textString = exp.getText();
        final int textLength = textString.length();
        final char lastChar = textString.charAt(textLength - 1);
        final boolean isLong = lastChar == 'l' || lastChar == 'L';
        if(isLong){
            textString = textString.substring(0, textLength - 1);
        }

        final BigInteger val;
        if(textString.charAt(0) == '0'){
            val = new BigInteger(textString, 8);
        } else{
            val = new BigInteger(textString, 10);
        }
        String hexString = "0x" + val.toString(16);
        if(isLong){
            hexString += 'L';
        }
        replaceExpression(hexString, exp);
    }
}
