/*
 * Copyright 2003-2005 Dave Griffith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.integer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

public class ConvertIntegerToDecimalIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ConvertIntegerToDecimalPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiLiteralExpression exp = (PsiLiteralExpression)element;
        final PsiType type = exp.getType();
        if(PsiType.INT.equals(type) || PsiType.LONG.equals(type)){
            @NonNls String textString = exp.getText();
            final int textLength = textString.length();
            final char lastChar = textString.charAt(textLength - 1);
            final boolean isLong = lastChar == 'l' || lastChar == 'L';
            if (isLong) {
                textString = textString.substring(0, textLength - 1);
            }
            final BigInteger val;
            if (textString.startsWith("0x")) {
                final String rawIntString = textString.substring(2);
                val = new BigInteger(rawIntString, 16);
            } else {
                final String rawIntString = textString.substring(1);
                val = new BigInteger(rawIntString, 8);
            }
            String decimalString = val.toString(10);
            if (isLong) {
                decimalString += 'L';
            }
            replaceExpression(decimalString, exp);
        } else {
            String textString = exp.getText();
            final int textLength = textString.length();
            final char lastChar = textString.charAt(textLength - 1);
            final boolean isFloat = lastChar == 'f' || lastChar == 'F';
            if (isFloat) {
                textString = textString.substring(0, textLength - 1);
            }
            if (isFloat) {
                final float floatValue = Float.parseFloat(textString);
                final String floatString = Float.toString(floatValue) + lastChar;
                replaceExpression(floatString, exp);
            } else {
                final double floatValue = Double.parseDouble(textString);
                final String floatString = Double.toString(floatValue);
                replaceExpression(floatString, exp);
            }
        }
    }
}