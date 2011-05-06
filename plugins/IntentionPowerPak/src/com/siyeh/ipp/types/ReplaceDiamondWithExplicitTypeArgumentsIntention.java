/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.ipp.types;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class ReplaceDiamondWithExplicitTypeArgumentsIntention extends Intention {

    @NotNull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new DiamondTypePredicate();
    }

    @Override
    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiJavaCodeReferenceElement)) {
            return;
        }
        final PsiJavaCodeReferenceElement javaCodeReferenceElement =
                (PsiJavaCodeReferenceElement) parent;
        final PsiReferenceParameterList referenceParameterList =
                (PsiReferenceParameterList) element;
        final StringBuilder text = new StringBuilder();
        text.append(javaCodeReferenceElement.getQualifiedName());
        text.append('<');
        final PsiType[] typeArguments = referenceParameterList.getTypeArguments();
        boolean first = true;
        for (PsiType typeArgument : typeArguments) {
            if (first) {
                first = false;
            } else {
                text.append(',');
            }
            text.append(typeArgument.getCanonicalText());
        }
        text.append('>');
        final PsiElementFactory elementFactory =
                JavaPsiFacade.getElementFactory(element.getProject());
        final PsiJavaCodeReferenceElement newReference =
                elementFactory.createReferenceFromText(text.toString(), element);
        javaCodeReferenceElement.replace(newReference);
    }
}
