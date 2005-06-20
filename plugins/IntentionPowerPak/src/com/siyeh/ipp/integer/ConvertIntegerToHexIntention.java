package com.siyeh.ipp.integer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

public class ConvertIntegerToHexIntention extends Intention{
    public String getText(){
        return "Convert to hex";
    }

    public String getFamilyName(){
        return "Convert To Hexadecimal";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new ConvertIntegerToHexPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiLiteralExpression exp = (PsiLiteralExpression) element;
        final PsiType type = exp.getType();
        if(type.equals(PsiType.INT) || type.equals(PsiType.LONG)){
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
        }else{
            String textString = exp.getText();
            final int textLength = textString.length();
            final char lastChar = textString.charAt(textLength - 1);
            final boolean isFloat= lastChar == 'f' || lastChar == 'F';
            if(isFloat){
                textString = textString.substring(0, textLength - 1);
            }

            if(isFloat){
                final float floatValue = Float.parseFloat(textString);
                final String floatString = Float.toHexString(floatValue)+lastChar;
                replaceExpression(floatString, exp);
            } else{
                final double floatValue = Double.parseDouble(textString);
                final String floatString = Double.toHexString(floatValue);
                replaceExpression(floatString, exp);
            }

        }
    }
}
