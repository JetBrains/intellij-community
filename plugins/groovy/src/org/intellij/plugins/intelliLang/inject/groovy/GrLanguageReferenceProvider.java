/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.openapi.util.Comparing;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.java.LanguageReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import static com.intellij.patterns.StandardPatterns.string;
import static org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns.groovyLiteralExpression;


/**
 * Created by Max Medvedev on 9/9/13
 */
public class GrLanguageReferenceProvider extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    final Configuration configuration = Configuration.getInstance();
    registrar.registerReferenceProvider(
      groovyLiteralExpression().annotationParam(string().with(isLanguageAnnotation(configuration)), "value").and(
        groovyLiteralExpression().with(isStringLiteral())), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
        return new PsiReference[]{new LanguageReference((PsiLiteral)element)};
      }
    });
  }

  private static PatternCondition<GrLiteral> isStringLiteral() {
    return new PatternCondition<GrLiteral>("isStringLiteral") {
      @Override
      public boolean accepts(@NotNull final GrLiteral expression, final ProcessingContext context) {
        return expression.getValue() instanceof String;
      }
    };
  }

  private static PatternCondition<String> isLanguageAnnotation(final Configuration configuration) {
    return new PatternCondition<String>("isLanguageAnnotation") {
      @Override
      public boolean accepts(@NotNull final String s, final ProcessingContext context) {
        return Comparing.equal(configuration.getAdvancedConfiguration().getLanguageAnnotationClass(), s);
      }
    };
  }
}
