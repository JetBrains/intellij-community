/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class GroovyNoVariantsDelegator extends CompletionContributor {

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    final boolean empty = result.runRemainingContributors(parameters, true).isEmpty();

    if (!empty && parameters.getInvocationCount() == 0) {
      result.restartCompletionWhenNothingMatches();
    }

    if (empty) {
      delegate(parameters, result);
    }
  }

  private static void delegate(CompletionParameters parameters, CompletionResultSet result) {
    if (parameters.getCompletionType() == CompletionType.BASIC) {
      if (parameters.getInvocationCount() <= 1 &&
          JavaCompletionContributor.mayStartClassName(result, false) &&
          GroovyCompletionContributor.isClassNamePossible(parameters.getPosition()) &&
          !MapArgumentCompletionProvider.isMapKeyCompletion(parameters)) {
        suggestNonImportedClasses(parameters, result);
      }

      suggestChainedCalls(parameters, result);
    }
  }

  private static void suggestNonImportedClasses(CompletionParameters parameters, CompletionResultSet result) {
    final ClassByNameMerger merger = new ClassByNameMerger(parameters.getInvocationCount() == 0, result);

    GroovyCompletionContributor.addAllClasses(parameters, new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        JavaPsiClassReferenceElement classElement =
          element.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
        if (classElement != null) {
          classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        }
        merger.consume(classElement);
      }
    }, new InheritorsHolder(parameters.getPosition(), result), result.getPrefixMatcher());

    merger.finishedClassProcessing();
  }

  private static void suggestChainedCalls(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiElement parent = position.getParent();
    if (!(parent instanceof GrReferenceElement)) {
      return;
    }
    PsiElement qualifier = ((GrReferenceElement)parent).getQualifier();
    if (!(qualifier instanceof GrReferenceElement) ||
        ((GrReferenceElement)qualifier).getQualifier() != null ||
        ((GrReferenceElement)qualifier).resolve() != null) {
      return;
    }

    String fullPrefix = position.getContainingFile().getText().substring(parent.getTextRange().getStartOffset(), parameters.getOffset());
    final CompletionResultSet qualifiedCollector = result.withPrefixMatcher(fullPrefix);
    InheritorsHolder inheritors = new InheritorsHolder(position, result);
    for (final LookupElement base : suggestQualifierItems(parameters, (GrReferenceElement)qualifier, inheritors)) {
      final PsiType type = JavaCompletionUtil.getLookupElementType(base);
      if (type != null && !PsiType.VOID.equals(type)) {
        GrReferenceElement ref = createMockReference(position, type, base);
        PsiElement refName = ref.getReferenceNameElement();
        assert refName != null;
        for (LookupElement element : GroovyCompletionContributor.completeReference(
          parameters.withPosition(refName, refName.getTextRange().getStartOffset()), ref, inheritors, result.getPrefixMatcher())) {
          qualifiedCollector.addElement(new JavaChainLookupElement(base, element) {
            @Override
            protected boolean shouldParenthesizeQualifier(PsiFile file, int startOffset, int endOffset) {
              return false;
            }
          });
        }
      }
    }
  }

  private static GrReferenceElement createMockReference(final PsiElement place, @NotNull PsiType qualifierType, LookupElement qualifierItem) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(place.getProject());
    if (qualifierItem.getObject() instanceof PsiClass) {
      return factory
        .createReferenceExpressionFromText(((PsiClass)qualifierItem.getObject()).getQualifiedName() + ".xxx", place);
    }

    return factory.createReferenceExpressionFromText("xxx.xxx",
                                                     ReferenceExpressionCompletionContributor
                                                       .createContextWithXxxVariable(place, qualifierType));
  }


  private static Set<LookupElement> suggestQualifierItems(CompletionParameters _parameters,
                                                          GrReferenceElement qualifier,
                                                          InheritorsHolder inheritors) {
    CompletionParameters parameters =
      _parameters.withPosition(qualifier.getReferenceNameElement(), qualifier.getTextRange().getEndOffset());
    String referenceName = qualifier.getReferenceName();
    if (referenceName == null) {
      return Collections.emptySet();
    }

    final PrefixMatcher qMatcher = new CamelHumpMatcher(referenceName);
    final Set<LookupElement> variants = new LinkedHashSet<LookupElement>();
    for (LookupElement element : GroovyCompletionContributor.completeReference(parameters, qualifier, inheritors, qMatcher)) {
      if (qMatcher.prefixMatches(element)) {
        variants.add(element);
      }
    }

    for (PsiClass aClass : PsiShortNamesCache.getInstance(qualifier.getProject()).getClassesByName(referenceName, qualifier.getResolveScope())) {
      variants.add(GroovyCompletionUtil.createClassLookupItem(aClass));
    }


    if (variants.isEmpty()) {
      GroovyCompletionContributor.addAllClasses(parameters, new Consumer<LookupElement>() {
        @Override
        public void consume(LookupElement element) {
          if (qMatcher.prefixMatches(element)) {
            variants.add(element);
          }
        }
      }, inheritors, qMatcher);
    }
    return variants;
  }


}
