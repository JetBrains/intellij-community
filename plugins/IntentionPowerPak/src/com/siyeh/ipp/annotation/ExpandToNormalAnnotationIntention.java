/*
 * Copyright 2010 Bas Leijdekkers
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
package com.siyeh.ipp.annotation;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ExpandToNormalAnnotationIntention extends MutablyNamedIntention {

    @Override
    protected String getTextForElement(PsiElement element) {
        final PsiAnnotation annotation = (PsiAnnotation) element;
        final String text = buildReplacementText(annotation);
        return IntentionPowerPackBundle.message(
                "expand.to.normal.annotation.name", text);
    }

    @NotNull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ExpandToNormalAnnotationPredicate();
    }

    public static String buildReplacementText(PsiAnnotation annotation) {
        final StringBuilder text = new StringBuilder("@");
        final PsiAnnotationParameterList parameterList =
                annotation.getParameterList();
        if (parameterList.getChildren().length == 0) {
            final PsiJavaCodeReferenceElement nameReferenceElement =
                    annotation.getNameReferenceElement();
            if (nameReferenceElement != null) {
                text.append(nameReferenceElement.getText());
            }
            text.append("()");
        } else {
            final PsiNameValuePair[] attributes = parameterList.getAttributes();
            final PsiNameValuePair attribute = attributes[0];
            final PsiAnnotationMemberValue value = attribute.getValue();
            final PsiJavaCodeReferenceElement nameReferenceElement =
                    annotation.getNameReferenceElement();
            if (nameReferenceElement != null) {
                text.append(nameReferenceElement.getText());
            }
            text.append("(value = ");
            text.append(value.getText());
            text.append(')');
        }
        return text.toString();
    }

    @Override
    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiAnnotation annotation = (PsiAnnotation) element;
        final PsiElementFactory factory =
                JavaPsiFacade.getElementFactory(annotation.getProject());
        final String text = buildReplacementText(annotation);
        final PsiAnnotation newAnnotation =
                factory.createAnnotationFromText(
                        text, annotation);
        annotation.replace(newAnnotation);
    }
}
