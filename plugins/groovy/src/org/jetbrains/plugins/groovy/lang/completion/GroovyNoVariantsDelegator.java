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
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnnotationTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class GroovyNoVariantsDelegator extends CompletionContributor {

  private static boolean suggestMetaAnnotations(CompletionParameters parameters) {
    PsiElement position = parameters.getPosition();
    return psiElement().withParents(GrCodeReferenceElement.class, GrAnnotation.class, GrModifierList.class, GrAnnotationTypeDefinition.class).accepts(position);
  }

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, CompletionResultSet result) {
    LinkedHashSet<CompletionResult> plainResults = result.runRemainingContributors(parameters, true);
    final boolean empty = JavaNoVariantsDelegator.containsOnlyPackages(plainResults) || suggestMetaAnnotations(parameters);

    if (!empty && parameters.getInvocationCount() == 0) {
      result.restartCompletionWhenNothingMatches();
    }

    if (empty) {
      delegate(parameters, result);
    } else if (Registry.is("ide.completion.show.better.matching.classes")) {
      if (parameters.getCompletionType() == CompletionType.BASIC &&
          parameters.getInvocationCount() <= 1 &&
          JavaCompletionContributor.mayStartClassName(result) &&
          GroovyCompletionContributor.isClassNamePossible(parameters.getPosition()) &&
          !MapArgumentCompletionProvider.isMapKeyCompletion(parameters) &&
          !GroovySmartCompletionContributor.AFTER_NEW.accepts(parameters.getPosition())) {
        result = result.withPrefixMatcher(new BetterPrefixMatcher(result.getPrefixMatcher(), BetterPrefixMatcher.getBestMatchingDegree(plainResults)));
        suggestNonImportedClasses(parameters, result);
      }
    }
  }

  private static void delegate(CompletionParameters parameters, CompletionResultSet result) {
    if (parameters.getCompletionType() == CompletionType.BASIC) {
      if (parameters.getInvocationCount() <= 1 &&
          JavaCompletionContributor.mayStartClassName(result) &&
          GroovyCompletionContributor.isClassNamePossible(parameters.getPosition()) &&
          !MapArgumentCompletionProvider.isMapKeyCompletion(parameters)) {
        suggestNonImportedClasses(parameters, result);
      }

      suggestChainedCalls(parameters, result);
    }
  }

  private static void suggestNonImportedClasses(CompletionParameters parameters, final CompletionResultSet result) {
    GroovyCompletionContributor.addAllClasses(parameters, new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        JavaPsiClassReferenceElement classElement =
          element.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
        if (classElement != null) {
          classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        }
        result.addElement(element);
      }
    }, new InheritorsHolder(parameters.getPosition(), result), result.getPrefixMatcher());
  }

  private static void suggestChainedCalls(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiElement parent = position.getParent();
    if (!(parent instanceof GrReferenceElement)) {
      return;
    }
    PsiElement qualifier = ((GrReferenceElement)parent).getQualifier();
    if (!(qualifier instanceof GrReferenceElement) ||
        ((GrReferenceElement)qualifier).getQualifier() != null) {
      return;
    }
    PsiElement target = ((GrReferenceElement)qualifier).resolve();
    if (target != null && !(target instanceof PsiPackage)) {
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
        CompletionParameters newParams = parameters.withPosition(refName, refName.getTextRange().getStartOffset());
        GroovyCompletionContributor.completeReference(newParams, ref, inheritors, result.getPrefixMatcher(), new Consumer<LookupElement>() {
          @Override
          public void consume(LookupElement element) {
            qualifiedCollector.addElement(new JavaChainLookupElement(base, element) {
              @Override
              protected boolean shouldParenthesizeQualifier(PsiFile file, int startOffset, int endOffset) {
                return false;
              }
            });
          }
        });
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
                                                     JavaCompletionUtil
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
    GroovyCompletionContributor.completeReference(parameters, qualifier, inheritors, qMatcher, new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement element) {
        if (qMatcher.prefixMatches(element)) {
          variants.add(element);
        }
      }
    });

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
