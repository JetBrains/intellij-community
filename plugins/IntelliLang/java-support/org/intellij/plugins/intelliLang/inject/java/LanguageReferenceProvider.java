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
package org.intellij.plugins.intelliLang.inject.java;

import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UExpression;

import java.util.Objects;

import static com.intellij.patterns.PsiJavaPatterns.literalExpression;
import static com.intellij.patterns.uast.UastPatterns.injectionHostUExpression;
import static com.intellij.psi.UastReferenceRegistrar.registerUastReferenceProvider;

/**
 * Provides references to Language-IDs and RegExp enums for completion.
 */
public final class LanguageReferenceProvider extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    final Configuration configuration = Configuration.getInstance();
    registerUastReferenceProvider(registrar, injectionHostUExpression().annotationParam(StandardPatterns.string().with(
      new PatternCondition<>(
        "isLanguageAnnotation") {
        @Override
        public boolean accepts(@NotNull final String s, final ProcessingContext context) {
          return Objects.equals(configuration.getAdvancedConfiguration().getLanguageAnnotationClass(), s);
        }
      }), "value"), new UastInjectionHostReferenceProvider() {

      @Override
      public boolean acceptsTarget(@NotNull PsiElement target) {
        return false;
      }

      @Override
      public PsiReference @NotNull [] getReferencesForInjectionHost(@NotNull UExpression uExpression,
                                                                    @NotNull PsiLanguageInjectionHost host,
                                                                    @NotNull ProcessingContext context) {
        return new PsiReference[]{new ULiteralLanguageReference(uExpression, host)};
      }
    }, PsiReferenceRegistrar.DEFAULT_PRIORITY);
    registrar.registerReferenceProvider(literalExpression().with(new PatternCondition<>("isStringLiteral") {
      @Override
      public boolean accepts(@NotNull final PsiLiteralExpression expression, final ProcessingContext context) {
        return PsiUtilEx.isStringOrCharacterLiteral(expression);
      }
    }), new PsiReferenceProvider() {

      @Override
      public boolean acceptsTarget(@NotNull PsiElement target) {
        return target instanceof PsiLiteral;
      }

      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext context) {
        final PsiLiteralExpression expression = (PsiLiteralExpression)psiElement;
        final PsiModifierListOwner owner =
          AnnotationUtilEx.getAnnotatedElementFor(expression, AnnotationUtilEx.LookupType.PREFER_DECLARATION);
        if (owner != null && PsiUtilEx.isLanguageAnnotationTarget(owner)) {
          final PsiAnnotation[] annotations = AnnotationUtilEx.getAnnotationFrom(owner, configuration.getAdvancedConfiguration().getPatternAnnotationPair(), true);
          if (annotations.length > 0) {
            final String pattern = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
            if (pattern != null) {
              return new PsiReference[]{new RegExpEnumReference(expression, pattern)};
            }
          }
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });
  }

}
