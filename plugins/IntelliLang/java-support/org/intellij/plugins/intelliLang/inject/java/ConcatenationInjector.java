/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.inject.java;

import com.intellij.lang.Language;
import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.ContextComputationProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author cdr
 */
public class ConcatenationInjector implements ConcatenationAwareInjector {
  private final Configuration myInjectionConfiguration;

  public ConcatenationInjector(Configuration configuration) {
    myInjectionConfiguration = configuration;
  }

  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull PsiElement... operands) {
    final PsiFile containingFile = operands[0].getContainingFile();
    processLiteralExpressionInjections(new PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>>() {
      public boolean process(final Language language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list) {
        InjectorUtils.registerInjection(language, list, containingFile, registrar);
        return true;
      }
    }, operands);
  }

  private boolean processAnnotationInjections(final boolean unparsable, final PsiModifierListOwner annoElement, final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor,
                                              final PsiElement... operands) {
    final PsiAnnotation[] annotations =
      AnnotationUtilEx.getAnnotationFrom(annoElement, myInjectionConfiguration.getLanguageAnnotationPair(), true);
    if (annotations.length > 0) {
      final String id = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
      final String prefix = AnnotationUtilEx.calcAnnotationValue(annotations, "prefix");
      final String suffix = AnnotationUtilEx.calcAnnotationValue(annotations, "suffix");
      final BaseInjection injection = new BaseInjection(LanguageInjectionSupport.JAVA_SUPPORT_ID);
      if (prefix != null) injection.setPrefix(prefix);
      if (suffix != null) injection.setSuffix(suffix);
      if (id != null) injection.setInjectedLanguageId(id);
      processInjectionWithContext(unparsable, injection, processor, operands);
      return true;
    }
    return false;
  }

  private void processLiteralExpressionInjections(final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor,
                                                  final PsiElement... operands) {
    processLiteralExpressionInjectionsInner(myInjectionConfiguration, new Processor<Info>() {
      public boolean process(final Info info) {
        if (processAnnotationInjections(info.unparsable, info.owner, processor, operands)) return false; // annotated element
        for (BaseInjection injection : info.injections) {
          processInjectionWithContext(info.unparsable, injection, processor, operands);
          if (injection.isTerminal()) {
            return false;
          }
        }
        return true;
      }
    }, operands);
  }

  public static class Info {
    final PsiModifierListOwner owner;
    final PsiMethod method;
    final List<BaseInjection> injections;
    final boolean unparsable;
    final int parameterIndex;

    public Info(final PsiModifierListOwner owner,
                final PsiMethod method,
                final List<BaseInjection> injections,
                final boolean unparsable,
                final int parameterIndex) {
      this.owner = owner;
      this.method = method;
      this.injections = injections;
      this.unparsable = unparsable;
      this.parameterIndex = parameterIndex;
    }
  }

  public static void processLiteralExpressionInjectionsInner(final Configuration configuration, final Processor<Info> processor,
                                                              final PsiElement... operands) {
    if (operands.length == 0) return;
    final PsiElement firstOperand = operands[0];
    final PsiElement topBlock = PsiUtil.getTopLevelEnclosingCodeBlock(firstOperand, null);
    final LocalSearchScope searchScope = new LocalSearchScope(new PsiElement[]{topBlock instanceof PsiCodeBlock
                                                                               ? topBlock : firstOperand.getContainingFile()}, "", true);
    final THashSet<PsiModifierListOwner> visitedVars = new THashSet<PsiModifierListOwner>();
    final LinkedList<PsiElement> places = new LinkedList<PsiElement>();
    places.add(firstOperand);
    boolean unparsable = false;
    while (!places.isEmpty()) {
      final PsiElement curPlace = places.removeFirst();
      final PsiModifierListOwner owner = AnnotationUtilEx.getAnnotatedElementFor(curPlace, AnnotationUtilEx.LookupType.PREFER_CONTEXT);
      if (owner == null) continue;

      final PsiMethod psiMethod;
      final int parameterIndex;
      if (owner instanceof PsiParameter) {
        final PsiElement declarationScope = ((PsiParameter)owner).getDeclarationScope();
        psiMethod = declarationScope instanceof PsiMethod? (PsiMethod)declarationScope : null;
        final PsiParameterList parameterList = psiMethod == null? null : ((PsiMethod)declarationScope).getParameterList();
        // don't check catchblock parameters & etc.
        if (parameterList == null || parameterList != owner.getParent()) continue;
        parameterIndex = parameterList.getParameterIndex((PsiParameter)owner);
      }
      else if (owner instanceof PsiMethod) {
        psiMethod = (PsiMethod)owner;
        parameterIndex = -1;
      }
      else if (configuration.isResolveReferences() &&
               owner instanceof PsiVariable && visitedVars.add(owner)) {
        final PsiVariable variable = (PsiVariable)owner;
        for (PsiReference psiReference : ReferencesSearch.search(variable, searchScope).findAll()) {
          final PsiElement element = psiReference.getElement();
          if (element instanceof PsiExpression) {
            final PsiExpression refExpression = (PsiExpression)element;
            places.add(refExpression);
            if (!unparsable) {
              unparsable = checkUnparsableReference(refExpression);  
            }
          }
        }
        parameterIndex = -1;
        psiMethod = null;
      }
      else {
        parameterIndex = -1;
        psiMethod = null;
      }
      final List<BaseInjection> injections;
      if (psiMethod == null) {
        injections = Collections.emptyList();
      }
      else {
        injections = ContainerUtil.findAll(configuration.getInjections(LanguageInjectionSupport.JAVA_SUPPORT_ID), new Condition<BaseInjection>() {
          public boolean value(final BaseInjection injection) {
            return injection.acceptsPsiElement(owner);
          }
        });
      }
      final Info info = new Info(owner, psiMethod, injections, unparsable, parameterIndex);
      if (!processor.process(info)) return;
    }
  }

  private static void processInjectionWithContext(final boolean unparsable, final BaseInjection injection,
                                                  final PairProcessor<Language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>> processor,
                                                  final PsiElement... operands) {
    final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
    if (language == null) return;
    final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

    final Ref<Boolean> unparsableRef = Ref.create(unparsable);
    final List<Object> objects = ContextComputationProcessor.collectOperands(injection.getPrefix(), injection.getSuffix(), unparsableRef, operands);
    if (objects.isEmpty()) return;
    final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result = new ArrayList<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>();
    final int len = objects.size();
    for (int i = 0; i < len; i++) {
      String curPrefix = null;
      Object o = objects.get(i);
      if (o instanceof String) {
        curPrefix = (String)o;
        if (i == len - 1) return; // IDEADEV-26751
        o = objects.get(++i);
      }
      String curSuffix = null;
      PsiLanguageInjectionHost curHost = null;
      if (o instanceof PsiLanguageInjectionHost) {
        curHost = (PsiLanguageInjectionHost)o;
        if (i == len-2) {
          final Object next = objects.get(i + 1);
          if (next instanceof String) {
            i ++;
            curSuffix = (String)next;
          }
        }
      }
      if (curHost == null) {
        unparsableRef.set(Boolean.TRUE);
      }
      else {
        if (!(curHost instanceof PsiLiteralExpression)) {
          result.add(Trinity.create(curHost, InjectedLanguage.create(injection.getInjectedLanguageId(), curPrefix, curSuffix, true),
                                  ElementManipulators.getManipulator(curHost).getRangeInElement(curHost)));
        }
        else {
          final List<TextRange> injectedArea = injection.getInjectedArea(curHost);
          for (int j = 0, injectedAreaSize = injectedArea.size(); j < injectedAreaSize; j++) {
            final TextRange textRange = injectedArea.get(j);
            result.add(Trinity.create(
              curHost, InjectedLanguage.create(injection.getInjectedLanguageId(),
                                               (separateFiles || j == 0? curPrefix: ""),
                                               (separateFiles || j == injectedAreaSize -1? curSuffix : ""),
                                               true), textRange));
          }
        }
      }
    }
    if (!result.isEmpty()) {
      if (separateFiles) {
        for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
          processor.process(language, Collections.singletonList(trinity));
        }
      }
      else {
        for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
          trinity.first.putUserData(LanguageInjectionSupport.HAS_UNPARSABLE_FRAGMENTS, unparsableRef.get());
        }
        processor.process(language, result);
      }
    }
  }

  private static boolean checkUnparsableReference(final PsiExpression refExpression) {
    final PsiElement parent = refExpression.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      final IElementType operation = assignmentExpression.getOperationTokenType();
      if (assignmentExpression.getLExpression() == refExpression && JavaTokenType.PLUSEQ.equals(operation)) {
        return true;
      }
    }
    else if (parent instanceof PsiBinaryExpression) {
      return true;
    }
    return false;
  }
}
