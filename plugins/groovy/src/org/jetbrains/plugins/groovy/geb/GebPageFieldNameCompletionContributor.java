/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.geb;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.util.FieldInitializerTailTypes;

/**
 * @author Sergey Evdokimov
 */
public class GebPageFieldNameCompletionContributor extends CompletionContributor {

  public GebPageFieldNameCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(GroovyTokenTypes.mIDENT).withParent(
      PsiJavaPatterns.psiField().withModifiers(PsiModifier.STATIC).inClass(PsiJavaPatterns.psiClass().inheritorOf(true, "geb.Page"))), new GebCompletionProvider());
  }

  private static class GebCompletionProvider extends CompletionProvider<CompletionParameters> {

    @SuppressWarnings("unchecked")
    private static final Pair<String, TailType>[] VARIANTS = new Pair[]{
      Pair.create("url", FieldInitializerTailTypes.EQ_STRING),
      Pair.create("content", FieldInitializerTailTypes.EQ_CLOSURE),
      Pair.create("at", FieldInitializerTailTypes.EQ_CLOSURE),
    };

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiClass.class);
      assert psiClass != null;

      for (Pair<String, TailType> trinity : VARIANTS) {
        String fieldName = trinity.first;

        if (psiClass.findFieldByName(fieldName, false) == null) {
          result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(fieldName), trinity.second));
        }
      }
    }
  }
}
