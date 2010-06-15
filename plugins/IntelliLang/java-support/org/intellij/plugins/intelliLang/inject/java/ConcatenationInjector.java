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
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PatternValuesIndex;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.Configuration;
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
  private final Configuration myConfiguration;
  private final Project myProject;
  private Pair<String, CachedValue<Collection<String>>> myAnnoIndex;
  private CachedValue<Collection<String>> myXmlIndex;


  public ConcatenationInjector(Configuration configuration, Project project) {
    myConfiguration = configuration;
    myProject = project;
  }

  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull PsiElement... operands) {
    if (operands.length == 0) return;
    final PsiFile containingFile = operands[0].getContainingFile();
    new InjectionProcessor(myConfiguration, operands) {
      @Override
      protected void processInjection(Language language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list) {
        InjectorUtils.registerInjection(language, list, containingFile, registrar);
      }

      @Override
      protected boolean areThereInjectionsWithName(String methodName, boolean annoOnly) {
        if (getAnnotatedElementsValue().contains(methodName)) {
          return true;
        }
        if (!annoOnly && getXmlAnnotatedElementsValue().contains(methodName)) {
          return true;
        }
        return false;
      }
    }.processInjections();
  }

  public static class InjectionProcessor {

    private final Configuration myConfiguration;
    private final PsiElement[] myOperands;
    private boolean myShouldStop;
    private boolean myUnparsable;

    public InjectionProcessor(Configuration configuration, PsiElement... operands) {
      myConfiguration = configuration;
      myOperands = operands;
    }

    public void processInjections() {
      final PsiElement firstOperand = myOperands[0];
      final PsiElement topBlock = PsiUtil.getTopLevelEnclosingCodeBlock(firstOperand, null);
      final LocalSearchScope searchScope = new LocalSearchScope(new PsiElement[]{topBlock instanceof PsiCodeBlock
                                                                                 ? topBlock : firstOperand.getContainingFile()}, "", true);
      final THashSet<PsiModifierListOwner> visitedVars = new THashSet<PsiModifierListOwner>();
      final LinkedList<PsiElement> places = new LinkedList<PsiElement>();
      places.add(firstOperand);
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
            methodName = classRef == null ? null : classRef.getReferenceName();
          }
          else {
            methodName = null;
          }
          if (methodName != null && areThereInjectionsWithName(methodName, false)) {
            final PsiMethod method = psiCallExpression.resolveMethod();
            final PsiParameter[] parameters = method == null ? PsiParameter.EMPTY_ARRAY : method.getParameterList().getParameters();
            if (index >= 0 && index < parameters.length && method != null) {
              process(parameters[index], method, index);
            }
          }
          return false;
        }

        public boolean visitMethodReturnStatement(PsiReturnStatement parent, PsiMethod method) {
          if (areThereInjectionsWithName(method.getName(), false)) {
            process(method, method, -1);
          }
          return false;
        }

        public boolean visitVariable(PsiVariable variable) {
          if (myConfiguration.isResolveReferences() && visitedVars.add(variable)) {
            for (PsiReference psiReference : ReferencesSearch.search(variable, searchScope).findAll()) {
              final PsiElement element = psiReference.getElement();
              if (element instanceof PsiExpression) {
                final PsiExpression refExpression = (PsiExpression)element;
                places.add(refExpression);
                if (!myUnparsable) {
                  myUnparsable = checkUnparsableReference(refExpression);
                }
              }
            }
          }
          process(variable, null, -1);
          return false;
        }

        public boolean visitAnnotationParameter(PsiNameValuePair nameValuePair, PsiAnnotation psiAnnotation) {
          final String paramName = nameValuePair.getName();
          final String methodName = paramName != null ? paramName : PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
          if (areThereInjectionsWithName(methodName, false)) {
            final PsiReference reference = nameValuePair.getReference();
            final PsiElement element = reference == null ? null : reference.resolve();
            if (element instanceof PsiMethod) {
              process((PsiMethod)element, (PsiMethod)element, -1);
            }
          }
          return false;
        }

        public boolean visitReference(PsiReferenceExpression expression) {
          if (!myConfiguration.isResolveReferences()) return true;
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
          return !myShouldStop;
        }
      };

      while (!places.isEmpty() && !myShouldStop) {
        final PsiElement curPlace = places.removeFirst();
        AnnotationUtilEx.visitAnnotatedElements(curPlace, visitor);
      }
    }

    private void process(final PsiModifierListOwner owner, PsiMethod method, int paramIndex) {
      if (!processAnnotationInjections(owner)) {
        myShouldStop = true;
      }
      for (BaseInjection injection : myConfiguration.getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID)) {
        if (injection.acceptsPsiElement(owner)) {
          if (!processXmlInjections(injection, owner, method, paramIndex)) {
            myShouldStop = true;
            break;
          }
        }
      }
    }

    protected boolean processAnnotationInjections(final PsiModifierListOwner annoElement) {
      final String checkName;
      if (annoElement instanceof PsiParameter) {
        final PsiElement scope = ((PsiParameter)annoElement).getDeclarationScope();
        checkName = scope instanceof PsiMethod ? ((PsiNamedElement)scope).getName() : ((PsiNamedElement)annoElement).getName();
      }
      else if (annoElement instanceof PsiNamedElement) {
        checkName = ((PsiNamedElement)annoElement).getName();
      }
      else checkName = null;
      if (checkName == null || !areThereInjectionsWithName(checkName, true)) return true;
      final PsiAnnotation[] annotations =
        AnnotationUtilEx.getAnnotationFrom(annoElement, myConfiguration.getLanguageAnnotationPair(), true);
      if (annotations.length > 0) {
        final String id = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
        final String prefix = AnnotationUtilEx.calcAnnotationValue(annotations, "prefix");
        final String suffix = AnnotationUtilEx.calcAnnotationValue(annotations, "suffix");
        final BaseInjection injection = new BaseInjection(LanguageInjectionSupport.JAVA_SUPPORT_ID);
        if (prefix != null) injection.setPrefix(prefix);
        if (suffix != null) injection.setSuffix(suffix);
        if (id != null) injection.setInjectedLanguageId(id);
        processInjectionWithContext(myUnparsable, injection);
        return false;
      }
      return true;
    }

    protected boolean processXmlInjections(BaseInjection injection, PsiModifierListOwner owner, PsiMethod method, int paramIndex) {
      processInjectionWithContext(myUnparsable, injection);
      if (injection.isTerminal()) {
        return false;
      }
      return true;
    }

    private void processInjectionWithContext(boolean unparsable, BaseInjection injection) {
      final Language language = InjectedLanguage.findLanguageById(injection.getInjectedLanguageId());
      if (language == null) return;
      final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

      final Ref<Boolean> unparsableRef = Ref.create(unparsable);
      final List<Object> objects = ContextComputationProcessor.collectOperands(injection.getPrefix(), injection.getSuffix(), unparsableRef, myOperands);
      if (objects.isEmpty()) return;
      final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result =
        new ArrayList<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>>();
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
          if (i == len - 2) {
            final Object next = objects.get(i + 1);
            if (next instanceof String) {
              i++;
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
                                                 (separateFiles || j == 0 ? curPrefix : ""),
                                                 (separateFiles || j == injectedAreaSize - 1 ? curSuffix : ""),
                                                 true), textRange));
            }
          }
        }
      }
      if (!result.isEmpty()) {
        if (separateFiles) {
          for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
            processInjection(language, Collections.singletonList(trinity));
          }
        }
        else {
          for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
            trinity.first.putUserData(LanguageInjectionSupport.HAS_UNPARSABLE_FRAGMENTS, unparsableRef.get());
          }
          processInjection(language, result);
        }
      }
    }

    protected void processInjection(Language language, List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list) {
    }

    protected boolean areThereInjectionsWithName(String methodName, boolean annoOnly) {
      return true;
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


  public Collection<String> getAnnotatedElementsValue() {
    // note: external annotations not supported
    final String annotationClass = myConfiguration.getLanguageAnnotationClass();
    if (myAnnoIndex == null || !Comparing.equal(myAnnoIndex.first, annotationClass)) {
      myAnnoIndex = Pair.create(annotationClass, CachedValuesManager.getManager(myProject).createCachedValue(new CachedValueProvider<Collection<String>>() {
        public Result<Collection<String>> compute() {
          final Collection<String> result = new THashSet<String>();
          final ArrayList<String> annoClasses = new ArrayList<String>(3);
          annoClasses.add(StringUtil.getShortName(annotationClass));
          for (int cursor = 0; cursor < annoClasses.size(); cursor++) {
            final String annoClass = annoClasses.get(cursor);
            for (PsiAnnotation annotation : JavaAnnotationIndex.getInstance()
              .get(annoClass, myProject, GlobalSearchScope.allScope(myProject))) {
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
          return new Result<Collection<String>>(result, PsiModificationTracker.MODIFICATION_COUNT, myConfiguration);
        }
      }, false));
    }
    return myAnnoIndex.second.getValue();
  }

  private Collection<String> getXmlAnnotatedElementsValue() {
    if (myXmlIndex == null) {
      myXmlIndex = CachedValuesManager.getManager(myProject).createCachedValue(new CachedValueProvider<Collection<String>>() {
        public Result<Collection<String>> compute() {
          final Map<ElementPattern<?>, BaseInjection> map = new THashMap<ElementPattern<?>, BaseInjection>();
          for (BaseInjection injection : myConfiguration.getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID)) {
            for (InjectionPlace place : injection.getInjectionPlaces()) {
              if (!place.isEnabled() || place.getElementPattern() == null) continue;
              map.put(place.getElementPattern(), injection);
            }
          }
          final Set<String> stringSet = PatternValuesIndex.buildStringIndex(map.keySet());
          final Result<Collection<String>> r = new Result<Collection<String>>(stringSet, myConfiguration);
          r.setLockValue(true);
          return r;
        }
      }, false);
    }
    return myXmlIndex.getValue();
  }
}
