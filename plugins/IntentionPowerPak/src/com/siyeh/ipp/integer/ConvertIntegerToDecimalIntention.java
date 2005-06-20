package com.siyeh.ipp.integer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

public class ConvertIntegerToDecimalIntention extends Intention{
    public String getText(){
        return "Convert to decimal";
    }

    public String getFamilyName(){
        return "Convert To Decimal";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new ConvertIntegerToDecimalPredicate();
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
            if(textString.startsWith("0x")){
                final String rawIntString = textString.substring(2);
                val = new BigInteger(rawIntString, 16);
            } else{
                final String rawIntString = textString.substring(1);
                val = new BigInteger(rawIntString, 8);
            }
            String decimalString = val.toString(10);
            if(isLong){
                decimalString += 'L';
            }
            replaceExpression(decimalString, exp);
        }
        else
        {
            String textString = exp.getText();

            final int textLength = textString.length();
            final char lastChar = textString.charAt(textLength - 1);
            final boolean isFloat = lastChar == 'f' || lastChar == 'F';
            if(isFloat){
                textString = textString.substring(0, textLength - 1);
            }

            if(isFloat){
                final float floatValue = Float.parseFloat(textString);
                final String floatString = Float.toString(floatValue)+lastChar;
                replaceExpression(floatString, exp);
            } else{
                final double floatValue = Double.parseDouble(textString);
                final String floatString = Double.toString(floatValue);
                replaceExpression(floatString, exp);
            }
        }
    }
}
