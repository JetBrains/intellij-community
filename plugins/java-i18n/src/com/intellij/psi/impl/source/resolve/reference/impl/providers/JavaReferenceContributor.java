/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.source.resolve.reference.CommentsReferenceContributor;
import com.intellij.psi.javadoc.PsiDocToken;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.XmlPatterns.xmlAttributeValue;
import static com.intellij.patterns.XmlPatterns.xmlTag;

public class JavaReferenceContributor extends PsiReferenceContributor{
  @Override
  public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
    final PsiReferenceProvider filePathReferenceProvider = new FilePathReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull String text, int offset, boolean soft) {
        PsiReference[] references = super.getReferencesByElement(element, text, offset, soft);
        return references.length > 100 ? PsiReference.EMPTY_ARRAY : references;
      }
    };
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return !JavaI18nUtil.mustBePropertyKey((PsiLiteralExpression) context, null);
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    })), filePathReferenceProvider, PsiReferenceRegistrar.LOWER_PRIORITY);

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDocToken.class),
                                        CommentsReferenceContributor.COMMENTS_REFERENCE_PROVIDER_TYPE.getProvider());
  }
}
