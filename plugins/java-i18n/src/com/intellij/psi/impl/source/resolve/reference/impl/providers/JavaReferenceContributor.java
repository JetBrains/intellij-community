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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.source.resolve.reference.CommentsReferenceContributor;
import com.intellij.psi.javadoc.PsiDocToken;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.patterns.XmlPatterns.xmlAttributeValue;
import static com.intellij.patterns.XmlPatterns.xmlTag;

/**
 * @author peter
 */
public class JavaReferenceContributor extends PsiReferenceContributor{
  public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {

    final JavaClassListReferenceProvider classListProvider = new JavaClassListReferenceProvider();
    registrar.registerReferenceProvider(xmlAttributeValue(), classListProvider, PsiReferenceRegistrar.LOWER_PRIORITY);
    registrar.registerReferenceProvider(xmlTag(), classListProvider, PsiReferenceRegistrar.LOWER_PRIORITY);

    final PsiReferenceProvider filePathReferenceProvider = new FilePathReferenceProvider();
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class).and(new FilterPattern(new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        PsiLiteralExpression literalExpression = (PsiLiteralExpression) context;
        final Map<String, Object> annotationParams = new HashMap<>();
        annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
        return !JavaI18nUtil.mustBePropertyKey(literalExpression, annotationParams);
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    })), filePathReferenceProvider);
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDocToken.class),
                                        CommentsReferenceContributor.COMMENTS_REFERENCE_PROVIDER_TYPE.getProvider());
  }
}
