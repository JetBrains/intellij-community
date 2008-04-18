/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.util.AnnotationParameterFilter;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;

/**
 * Provides references to Language-IDs and RegExp enums for completion.
 */
final class LanguageReferenceProvider extends PsiReferenceProvider {
    private final Configuration myConfig = Configuration.getInstance();

    private final Computable<String> ANNOTATION_NAME = new Computable<String>() {
        public String compute() {
            return myConfig.getLanguageAnnotationClass();
        }
    };

    @NotNull
    public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext context) {
        if (PsiUtilEx.isStringLiteral(psiElement)) {
            final PsiLiteralExpression expression = (PsiLiteralExpression)psiElement;

            final PsiNameValuePair valuePair = PsiTreeUtil.getParentOfType(psiElement, PsiNameValuePair.class, true,
                    PsiStatement.class, PsiMember.class, PsiFile.class);

            if (AnnotationParameterFilter.isAccepted(valuePair, "value", ANNOTATION_NAME)) {
                assert valuePair != null;

                final PsiAnnotationMemberValue value = valuePair.getValue();
                if (value == psiElement) {
                    return new PsiReference[]{
                            new LanguageReference(expression)
                    };
                }
            } else {
                final PsiModifierListOwner owner = AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.PREFER_DECLARATION);
                if (owner != null) {
                    final PsiAnnotation[] annotations = AnnotationUtilEx.getAnnotationFrom(owner, myConfig.getPatternAnnotationPair(), true);
                    if (annotations.length > 0) {
                        final String pattern = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
                        return new PsiReference[]{
                                new RegExpEnumReference(expression, pattern)
                        };
                    }
                }
            }
        }
        return PsiReference.EMPTY_ARRAY;
    }
}
