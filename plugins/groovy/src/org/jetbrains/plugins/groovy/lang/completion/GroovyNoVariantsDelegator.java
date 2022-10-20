// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class GroovyNoVariantsDelegator extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance(GroovyNoVariantsDelegator.class);

  private static boolean suggestAnnotations(CompletionParameters parameters) {
    return PsiJavaPatterns.psiElement().withParents(GrCodeReferenceElement.class, GrAnnotation.class).accepts(parameters.getPosition());
  }

  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull CompletionResultSet result) {
    JavaNoVariantsDelegator.ResultTracker tracker = new JavaNoVariantsDelegator.ResultTracker(result);
    result.runRemainingContributors(parameters, tracker);
    final boolean empty = tracker.containsOnlyPackages || suggestAnnotations(parameters);

    if (GrMainCompletionProvider.isClassNamePossible(parameters.getPosition()) && !JavaCompletionContributor.mayStartClassName(result)) {
      result.restartCompletionOnAnyPrefixChange();
    }

    if (empty) {
      delegate(parameters, result, tracker.session);
    } else {
      if (parameters.getCompletionType() == CompletionType.BASIC &&
          parameters.getInvocationCount() <= 1 &&
          JavaCompletionContributor.mayStartClassName(result) &&
          GrMainCompletionProvider.isClassNamePossible(parameters.getPosition()) &&
          !MapArgumentCompletionProvider.isMapKeyCompletion(parameters)) {
        result = result.withPrefixMatcher(tracker.betterMatcher);
        suggestNonImportedClasses(parameters, result, tracker.session);
      }
    }
  }

  private static void delegate(CompletionParameters parameters, CompletionResultSet result, JavaCompletionSession session) {
    if (parameters.getCompletionType() == CompletionType.BASIC) {
      if (parameters.getInvocationCount() <= 1 &&
          (JavaCompletionContributor.mayStartClassName(result) || suggestAnnotations(parameters)) &&
          GrMainCompletionProvider.isClassNamePossible(parameters.getPosition()) &&
          !MapArgumentCompletionProvider.isMapKeyCompletion(parameters)) {
        suggestNonImportedClasses(parameters, result, session);
      }

      suggestChainedCalls(parameters, result);
    }
  }

  private static void suggestNonImportedClasses(CompletionParameters parameters, final CompletionResultSet result, JavaCompletionSession session) {
    GrMainCompletionProvider.addAllClasses(parameters, element -> {
      JavaPsiClassReferenceElement classElement =
        element.as(JavaPsiClassReferenceElement.CLASS_CONDITION_KEY);
      if (classElement != null) {
        classElement.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
      }
      result.addElement(element);
    }, session, result.getPrefixMatcher());
  }

  private static void suggestChainedCalls(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiElement parent = position.getParent();
    if (!(parent instanceof GrReferenceElement)) {
      return;
    }
    PsiElement qualifier = ((GrReferenceElement<?>)parent).getQualifier();
    if (!(qualifier instanceof GrReferenceElement) ||
        ((GrReferenceElement<?>)qualifier).getQualifier() != null) {
      return;
    }
    PsiElement target = ((GrReferenceElement<?>)qualifier).resolve();
    if (target != null && !(target instanceof PsiPackage)) {
      return;
    }

    String fullPrefix = position.getContainingFile().getText().substring(parent.getTextRange().getStartOffset(), parameters.getOffset());
    final CompletionResultSet qualifiedCollector = result.withPrefixMatcher(fullPrefix);
    JavaCompletionSession session = new JavaCompletionSession(result);
    for (final LookupElement base : suggestQualifierItems(parameters, (GrReferenceElement<?>)qualifier, session)) {
      final PsiType type = getPsiType(base.getObject());
      if (type != null && !PsiType.VOID.equals(type)) {
        GrReferenceElement<?> ref = createMockReference(position, type, base);
        PsiElement refName = ref == null ? null : ref.getReferenceNameElement();
        if (refName == null) continue;

        CompletionParameters newParams = parameters.withPosition(refName, refName.getTextRange().getStartOffset());
        GrMainCompletionProvider.completeReference(newParams, ref, session, result.getPrefixMatcher(), result, element ->
          qualifiedCollector.addElement(new JavaChainLookupElement(base, element) {
            @Override
            protected boolean shouldParenthesizeQualifier(PsiFile file, int startOffset, int endOffset) {
              return false;
            }
          }));
      }
    }
  }

  @Nullable
  private static PsiType getPsiType(final Object o) {
    if (o instanceof ResolveResult) {
      return getPsiType(((ResolveResult)o).getElement());
    }
    if (o instanceof PsiVariable) {
      return ((PsiVariable)o).getType();
    }
    else if (o instanceof PsiMethod) {
      return ((PsiMethod)o).getReturnType();
    }
    else if (o instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)o;
      return JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
    }
    return null;
  }

  @Nullable
  private static GrReferenceElement<?> createMockReference(final PsiElement place, @NotNull PsiType qualifierType, LookupElement qualifierItem) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(place.getProject());
    if (qualifierItem.getObject() instanceof PsiClass) {
      try {
        return factory
          .createReferenceExpressionFromText(((PsiClass)qualifierItem.getObject()).getQualifiedName() + ".xxx", place);
      }
      catch (IncorrectOperationException e) {
        LOG.debug(e);
        return null;
      }
    }

    return factory.createReferenceExpressionFromText("xxx.xxx",
                                                     JavaCompletionUtil
                                                       .createContextWithXxxVariable(place, qualifierType));
  }


  private static Set<LookupElement> suggestQualifierItems(CompletionParameters _parameters,
                                                          GrReferenceElement<?> qualifier,
                                                          JavaCompletionSession session) {
    String referenceName = qualifier.getReferenceName();
    if (referenceName == null) {
      return Collections.emptySet();
    }
    PsiElement nameElement = qualifier.getReferenceNameElement();
    if (nameElement == null) {
      return Collections.emptySet();
    }
    CompletionParameters parameters = _parameters.withPosition(nameElement, qualifier.getTextRange().getEndOffset());

    final PrefixMatcher qMatcher = new CamelHumpMatcher(referenceName);
    final Set<LookupElement> variants = new LinkedHashSet<>();
    GrMainCompletionProvider.completeReference(parameters, qualifier, session, qMatcher, null, element -> {
      if (qMatcher.prefixMatches(element)) {
        variants.add(element);
      }
    });

    for (PsiClass aClass : PsiShortNamesCache.getInstance(qualifier.getProject()).getClassesByName(referenceName, qualifier.getResolveScope())) {
      variants.add(GroovyCompletionUtil.createClassLookupItem(aClass));
    }


    if (variants.isEmpty()) {
      GrMainCompletionProvider.addAllClasses(parameters, element -> {
        if (qMatcher.prefixMatches(element)) {
          variants.add(element);
        }
      }, session, qMatcher);
    }
    return variants;
  }


}
