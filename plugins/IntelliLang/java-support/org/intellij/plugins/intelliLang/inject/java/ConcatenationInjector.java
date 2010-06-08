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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.PatternBasedInjectionHelper;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.ContextComputationProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
    final String checkName;
    if (annoElement instanceof PsiParameter) {
      final PsiElement scope = ((PsiParameter)annoElement).getDeclarationScope();
      checkName = scope instanceof PsiMethod ? ((PsiNamedElement)scope).getName() : ((PsiNamedElement)annoElement).getName();
    }
    else if (annoElement instanceof PsiNamedElement) {
      checkName = ((PsiNamedElement)annoElement).getName();
    }
    else checkName = null;
    if (checkName == null || !getAnnotatedElementsValue(annoElement.getProject(), myInjectionConfiguration).contains(checkName)) return false;
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
    final Ref<Boolean> unparsableRef = Ref.create(Boolean.FALSE);
    final Ref<Boolean> stopRef = Ref.create(Boolean.FALSE);
    final AnnotationUtilEx.AnnotatedElementVisitor visitor = new AnnotationUtilEx.AnnotatedElementVisitor() {
      public boolean visitMethodParameter(PsiExpression expression, PsiCallExpression psiCallExpression) {
        final PsiExpressionList list = psiCallExpression.getArgumentList();
        assert list != null;
        final int index = ArrayUtil.indexOf(list.getExpressions(), expression);
        final String methodName;
        if (psiCallExpression instanceof PsiMethodCallExpression) {
          methodName = ((PsiMethodCallExpression)psiCallExpression).getMethodExpression().getReferenceName();
        }
        else if (psiCallExpression instanceof PsiNewExpression) {
          final PsiJavaCodeReferenceElement classRef = ((PsiNewExpression)psiCallExpression).getClassOrAnonymousClassReference();
          methodName = classRef == null? null : classRef.getReferenceName();
        }
        else methodName = null;
        if (methodName != null && areThereInjectionsWithName(expression.getProject(), configuration, methodName)) {
          final PsiMethod method = psiCallExpression.resolveMethod();
          final PsiParameter[] parameters = method == null ? PsiParameter.EMPTY_ARRAY : method.getParameterList().getParameters();
          if (index >= 0 && index < parameters.length && method != null) {
            process(parameters[index], method, index);
          }
        }
        return false;
      }

      public boolean visitMethodReturnStatement(PsiReturnStatement parent, PsiMethod method) {
        if (areThereInjectionsWithName(parent.getProject(), configuration, method.getName())) {
          process(method, method, -1);
        }
        return false;
      }

      public boolean visitVariable(PsiVariable variable) {
        if (configuration.isResolveReferences() && visitedVars.add(variable)) {
          for (PsiReference psiReference : ReferencesSearch.search(variable, searchScope).findAll()) {
            final PsiElement element = psiReference.getElement();
            if (element instanceof PsiExpression) {
              final PsiExpression refExpression = (PsiExpression)element;
              places.add(refExpression);
              if (!unparsableRef.get()) {
                unparsableRef.set(checkUnparsableReference(refExpression));
              }
            }
          }
        }
        process(variable, null, -1);
        return false;
      }

      public boolean visitAnnotationParameter(PsiNameValuePair nameValuePair, PsiAnnotation psiAnnotation) {
        final String paramName = nameValuePair.getName();
        final String methodName = paramName != null? paramName : PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
        if (areThereInjectionsWithName(nameValuePair.getProject(), configuration, methodName)) {
          final PsiReference reference = nameValuePair.getReference();
          final PsiElement element = reference == null ? null : reference.resolve();
          if (element instanceof PsiMethod) {
            process((PsiMethod)element, (PsiMethod)element, -1);
          }
        }
        return false;
      }

      public boolean visitReference(PsiReferenceExpression expression) {
        if (!configuration.isResolveReferences()) return true;
        final PsiElement e = expression.resolve();
        if (e instanceof PsiVariable) {
          if (e instanceof PsiParameter) {
            final PsiParameter p = (PsiParameter)e;
            final PsiElement declarationScope = p.getDeclarationScope();
            final PsiMethod method = declarationScope instanceof PsiMethod ? (PsiMethod)declarationScope : null;
            final PsiParameterList parameterList = method == null ? null : method.getParameterList();
            // don't check catchblock parameters & etc.
            if (!(parameterList == null || parameterList != e.getParent())) {
              final int parameterIndex = parameterList.getParameterIndex((PsiParameter)e);
              process((PsiModifierListOwner)e, method, parameterIndex);
            }
          }
          visitVariable((PsiVariable)e);
        }
        return !stopRef.get();
      }

      private void process(final PsiModifierListOwner owner, PsiMethod method, int paramIndex) {
        final List<BaseInjection> injections =
          ContainerUtil.findAll(configuration.getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID), new Condition<BaseInjection>() {
            public boolean value(final BaseInjection injection) {
              return injection.acceptsPsiElement(owner);
            }
          });
        final Info info = new Info(owner, method, injections, unparsableRef.get(), paramIndex);
        stopRef.set(!processor.process(info));
      }
    };

    while (!places.isEmpty() && !stopRef.get()) {
      final PsiElement curPlace = places.removeFirst();
      AnnotationUtilEx.visitAnnotatedElements(curPlace, visitor);
    }
  }

  private static boolean areThereInjectionsWithName(Project project,
                                                    Configuration configuration,
                                                    String methodName) {
    return getXmlAnnotatedElementsValue(project).contains(methodName) ||
         getAnnotatedElementsValue(project, configuration).contains(methodName);
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


  private static Key<Pair<String, CachedValue<Collection<String>>>> LANGUAGE_ANNOTATED_STUFF = Key.create("LANGUAGE_ANNOTATED_STUFF");

  private static Collection<String> getAnnotatedElementsValue(final Project project, final Configuration configuration) {
    // note: external annotations not supported
    final String annotationClass = configuration.getLanguageAnnotationClass();
    Pair<String, CachedValue<Collection<String>>> userData = project.getUserData(LANGUAGE_ANNOTATED_STUFF);
    if (userData == null || !Comparing.equal(userData.first, annotationClass)) {
      userData = Pair.create(annotationClass, CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<Collection<String>>() {
        public Result<Collection<String>> compute() {
          final Collection<String> result = new THashSet<String>();
          final ArrayList<String> annoClasses = new ArrayList<String>(3);
          annoClasses.add(StringUtil.getShortName(annotationClass));
          for (int cursor = 0; cursor < annoClasses.size(); cursor++) {
            final String annoClass = annoClasses.get(cursor);
            for (PsiAnnotation annotation : JavaAnnotationIndex.getInstance()
              .get(annoClass, project, GlobalSearchScope.allScope(project))) {
              final PsiElement modList = annotation.getParent();
              if (!(modList instanceof PsiModifierList)) continue;
              final PsiElement element = modList.getParent();
              if (element instanceof PsiParameter) {
                final PsiElement scope = ((PsiParameter)element).getDeclarationScope();
                if (scope instanceof PsiNamedElement) {
                  ContainerUtil.addIfNotNull(((PsiNamedElement)scope).getName(), result);
                }
                else {
                  ContainerUtil.addIfNotNull(((PsiNamedElement)element).getName(), result);
                }
              }
              else if (element instanceof PsiNamedElement) {
                if (element instanceof PsiClass && ((PsiClass)element).isAnnotationType()) {
                  final String s = ((PsiClass)element).getName();
                  if (!annoClasses.contains(s)) annoClasses.add(s);
                }
                else {
                  ContainerUtil.addIfNotNull(((PsiNamedElement)element).getName(), result);
                }
              }
            }
          }
          return new Result<Collection<String>>(result, PsiModificationTracker.MODIFICATION_COUNT, configuration);
        }
      }, false));
      project.putUserData(LANGUAGE_ANNOTATED_STUFF, userData);
    }
    return userData.second.getValue();
  }

  private static Key<CachedValue<Collection<String>>> XML_ANNOTATED_STUFF = Key.create("XML_ANNOTATED_STUFF");

  private static Collection<String> getXmlAnnotatedElementsValue(final Project project) {
    CachedValue<Collection<String>> userData = project.getUserData(XML_ANNOTATED_STUFF);
    if (userData == null) {
      userData = CachedValuesManager.getManager(project).createCachedValue(new CachedValueProvider<Collection<String>>() {
        public Result<Collection<String>> compute() {
          final Configuration configuration = Configuration.getInstance();
          final Collection<String> result = new THashSet<String>();
          final PatternBasedInjectionHelper helper = new PatternBasedInjectionHelper(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID) {
            @Override
            protected void preInvoke(Object target, String methodName, Object[] arguments) {
              if (arguments.length == 1 && arguments[0] instanceof String) {
                if ("withName".equals(methodName)) {
                  result.add((String)arguments[0]);
                }
                else if ("definedInClass".equals(methodName)) {
                  result.add(StringUtil.getShortName((Class)arguments[0]));
                }
              }
            }
          };
          for (BaseInjection injection : configuration.getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID)) {
            for (InjectionPlace place : injection.getInjectionPlaces()) {
              try {
                helper.compileElementPattern(place.getText());
              }
              catch (Exception e) {
                // do nothing
              }
            }
          }
          final Result<Collection<String>> r = new Result<Collection<String>>(result, configuration);
          r.setLockValue(true);
          return r;
        }
      }, false);
      project.putUserData(XML_ANNOTATED_STUFF, userData);
    }
    return userData.getValue();
  }
}
