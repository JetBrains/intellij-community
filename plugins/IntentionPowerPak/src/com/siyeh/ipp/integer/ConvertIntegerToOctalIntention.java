/*
 * Copyright 2003-2008 Dave Griffith, Bsa Leijdekkers
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

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

public class ConvertIntegerToOctalIntention extends Intention {

    private static final BigInteger LONG_BINARY_ONES =
            new BigInteger("ffffffffffffffff", 16);
    private static final BigInteger INT_BINARY_ONES =
            new BigInteger("ffffffff", 16);

    @Override @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ConvertIntegerToOctalPredicate();
    }

    @Override
    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiExpression expression = (PsiExpression)element;
        @NonNls String textString = expression.getText();
        final int textLength = textString.length();
        final char lastChar = textString.charAt(textLength - 1);
        final boolean isLong = lastChar == 'l' || lastChar == 'L';
        if (isLong) {
            textString = textString.substring(0, textLength - 1);
        }
        BigInteger value;
        if (textString.startsWith("0x")) {
            final String rawTextString = textString.substring(2);
            value = new BigInteger(rawTextString, 16);
        } else {
            value = new BigInteger(textString, 10);
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression)parent;
                final IElementType tokenType =
                        prefixExpression.getOperationTokenType();
                if (JavaTokenType.MINUS == tokenType) {
                    if (isLong) {
                        value = value.xor(LONG_BINARY_ONES).add(BigInteger.ONE);
                    } else {
                        value = value.xor(INT_BINARY_ONES).add(BigInteger.ONE);
                    }
                    @NonNls String hexString = '0' + value.toString(8);
                    if (isLong) {
                        hexString += 'L';
                    }
                    replaceExpression(hexString, prefixExpression);
                    return;
                }
            }
        }
        String octString = '0' + value.toString(8);
        if (isLong) {
            octString += 'L';
        }
        replaceExpression(octString, expression);
    }
}
