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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.Set;

/**
 * @author ilyas
 */
public class GroovyCompletionContributor extends CompletionContributor {

  private static final ElementPattern<PsiElement> AFTER_NUMBER_LITERAL = PlatformPatterns.psiElement().afterLeafSkipping(
    StandardPatterns.alwaysFalse(),
    PlatformPatterns.psiElement().withElementType(PsiJavaPatterns
                                                    .elementType().oneOf(GroovyTokenTypes.mNUM_DOUBLE, GroovyTokenTypes.mNUM_INT,
                                                                         GroovyTokenTypes.mNUM_LONG, GroovyTokenTypes.mNUM_FLOAT,
                                                                         GroovyTokenTypes.mNUM_BIG_INT, GroovyTokenTypes.mNUM_BIG_DECIMAL)));


  public GroovyCompletionContributor() {
    GrMethodOverrideCompletionProvider.register(this);
    GrThisSuperCompletionProvider.register(this);
    MapArgumentCompletionProvider.register(this);
    GroovyConfigSlurperCompletionProvider.register(this);
    MapKeysCompletionProvider.register(this);
    GroovyDocCompletionProvider.register(this);
    GrStatementStartCompletionProvider.register(this);
    GrMainCompletionProvider.register(this);
    GrAnnotationAttributeCompletionProvider.register(this);

    extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(GrLiteral.class), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final Set<String> usedWords = new THashSet<>();
        for (CompletionResult element : result.runRemainingContributors(parameters, true)) {
          usedWords.add(element.getLookupElement().getLookupString());
        }

        PsiReference reference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
        if (reference == null || reference.isSoft()) {
          WordCompletionContributor.addWordCompletionVariants(result, parameters, usedWords);
        }
      }
    });

  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (!AFTER_NUMBER_LITERAL.accepts(parameters.getPosition())) {
      super.fillCompletionVariants(parameters, result);
    }
  }


  @Override
  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final String identifier = new GrDummyIdentifierProvider(context).getIdentifier();
    if (identifier != null) {
      context.setDummyIdentifier(identifier);
    }

    //don't eat $ from gstrings when completing previous injection ref. see IDEA-110369
    PsiElement position = context.getFile().findElementAt(context.getStartOffset());
    if (position!= null && position.getNode().getElementType() == GroovyTokenTypes.mDOLLAR) {
      context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, context.getStartOffset());
    }
  }
}
