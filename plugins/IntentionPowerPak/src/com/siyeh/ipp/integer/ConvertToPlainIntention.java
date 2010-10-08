/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * @author Konstantin Bulenkov
 */
public class ConvertToPlainIntention extends Intention {

    private static final ConvertToPlainPredicate PREDICATE =
            new ConvertToPlainPredicate();

    @Override
    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        try {
            final String elementText = element.getText();
            if (elementText.length() == 0) {
                return;
            }
            final int lastIndex = elementText.length() - 1;
            final char lastChar = elementText.charAt(lastIndex);
            if (lastChar == 'f' || lastChar == 'F') {
                final BigDecimal bigDecimal =
                        new BigDecimal(elementText.substring(0, lastIndex));
                replaceExpression(bigDecimal.toPlainString() + lastChar,
                        (PsiExpression) element);
            } else {
                final BigDecimal bigDecimal = new BigDecimal(elementText);
                replaceExpression(bigDecimal.toPlainString(),
                        (PsiExpression) element);
            }
        } catch (Exception e) {//
        }
    }

    @NotNull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return PREDICATE;
    }
}
