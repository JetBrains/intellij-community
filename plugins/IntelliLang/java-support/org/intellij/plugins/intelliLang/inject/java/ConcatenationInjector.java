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
package org.intellij.plugins.intelliLang.inject.java;

import com.intellij.lang.Language;
import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.ContextComputationProcessor;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author cdr
 */
public class ConcatenationInjector implements ConcatenationAwareInjector {
  private final Configuration myConfiguration;
  private final Project myProject;
  private final TemporaryPlacesRegistry myTemporaryPlacesRegistry;

  private final LanguageInjectionSupport mySupport;


  public ConcatenationInjector(Configuration configuration, Project project, TemporaryPlacesRegistry temporaryPlacesRegistry) {
    myConfiguration = configuration;
    myProject = project;
    myTemporaryPlacesRegistry = temporaryPlacesRegistry;
    mySupport = InjectorUtils.findNotNullInjectionSupport(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID);

  }

  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull PsiElement... operands) {
    if (operands.length == 0) return;
    boolean hasLiteral = false;
    InjectedLanguage tempInjectedLanguage = null;
    PsiFile containingFile = null;
    for (PsiElement operand : operands) {
      if (PsiUtilEx.isStringOrCharacterLiteral(operand)) {
        hasLiteral = true;
        if (containingFile == null) {
          containingFile = operands[0].getContainingFile();
        }

        tempInjectedLanguage = myTemporaryPlacesRegistry.getLanguageFor((PsiLanguageInjectionHost)operand, containingFile);
        if (tempInjectedLanguage != null) break;
      }
    }
    if (!hasLiteral) return;
    final Language tempLanguage = tempInjectedLanguage == null ? null : tempInjectedLanguage.getLanguage();
    final PsiFile finalContainingFile = containingFile;
    InjectionProcessor injectionProcessor = new InjectionProcessor(myConfiguration, mySupport, operands) {
      @Override
      protected void processInjection(Language language,
                                      List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list,
                                      boolean settingsAvailable,
                                      boolean unparsable) {
        InjectorUtils.registerInjection(language, list, finalContainingFile, registrar);
        InjectorUtils.registerSupport(mySupport, settingsAvailable, registrar);
        InjectorUtils.putInjectedFileUserData(registrar, InjectedLanguageUtil.FRANKENSTEIN_INJECTION, unparsable ? Boolean.TRUE : null);
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
    };
    if (tempLanguage != null) {
      BaseInjection baseInjection = new BaseInjection(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID);
      baseInjection.setInjectedLanguageId(tempInjectedLanguage.getID());
      injectionProcessor.processInjectionInner(baseInjection, false);
      InjectorUtils.putInjectedFileUserData(registrar, LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE, tempInjectedLanguage);
    }
    else {
      injectionProcessor.processInjections();
    }
  }

  public static class InjectionProcessor {

    private final Configuration myConfiguration;
    private final LanguageInjectionSupport mySupport;
    private final PsiElement[] myOperands;
    private boolean myShouldStop;
    private boolean myUnparsable;

    public InjectionProcessor(Configuration configuration, LanguageInjectionSupport support, PsiElement... operands) {
      myConfiguration = configuration;
      mySupport = support;
      myOperands = operands;
    }

    public void processInjections() {
      final PsiElement firstOperand = myOperands[0];
      final PsiElement topBlock = PsiUtil.getTopLevelEnclosingCodeBlock(firstOperand, null);
      final LocalSearchScope searchScope = new LocalSearchScope(new PsiElement[]{topBlock instanceof PsiCodeBlock
                                                                                 ? topBlock : firstOperand.getContainingFile()}, "", true);
      final THashSet<PsiModifierListOwner> visitedVars = new THashSet<>();
      final LinkedList<PsiElement> places = new LinkedList<>();
      places.add(firstOperand);
      class MyAnnoVisitor implements AnnotationUtilEx.AnnotatedElementVisitor {
        public boolean visitMethodParameter(PsiExpression expression, PsiCallExpression psiCallExpression) {
          final PsiExpressionList list = psiCallExpression.getArgumentList();
          assert list != null;
          final int index = ArrayUtil.indexOf(list.getExpressions(), expression);
          final String methodName;
          if (psiCallExpression instanceof PsiMethodCallExpression) {
            final String referenceName = ((PsiMethodCallExpression)psiCallExpression).getMethodExpression().getReferenceName();
            if ("super".equals(referenceName) || "this".equals(referenceName)) { // constructor call
              final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiCallExpression, PsiClass.class, true);
              final PsiClass psiTargetClass = "super".equals(referenceName)? psiClass == null ? null : psiClass.getSuperClass() : psiClass;
              methodName = psiTargetClass == null? null : psiTargetClass.getName();
            }
            else {
              methodName = referenceName;
            }
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

        public boolean visitMethodReturnStatement(PsiElement source, PsiMethod method) {
          if (areThereInjectionsWithName(method.getName(), false)) {
            process(method, method, -1);
          }
          return false;
        }

        private void visitVariableUsages(PsiVariable variable) {
          if (variable == null) return;
          if (myConfiguration.getAdvancedConfiguration().getDfaOption() != Configuration.DfaOption.OFF && visitedVars.add(variable)) {
            ReferencesSearch.search(variable, searchScope).forEach(psiReference -> {
              final PsiElement element = psiReference.getElement();
              if (element instanceof PsiExpression) {
                final PsiExpression refExpression = (PsiExpression)element;
                places.add(refExpression);
                if (!myUnparsable) {
                  myUnparsable = checkUnparsableReference(refExpression);
                }
              }
              return true;
            });
          }
        }

        public boolean visitVariable(PsiVariable variable) {
          visitVariableUsages(variable);
          PsiElement anchor = !(variable.getFirstChild() instanceof PsiComment) ? variable :
                              variable.getModifierList() != null ? variable.getModifierList() :
                              variable.getTypeElement();

          if (anchor != null && !processCommentInjection(anchor)) {
            myShouldStop = true;
          }
          else if (areThereInjectionsWithName(variable.getName(), false)) {
            process(variable, null, -1);
          }
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
          if (myConfiguration.getAdvancedConfiguration().getDfaOption() == Configuration.DfaOption.OFF) return true;
          final PsiElement e = expression.resolve();
          if (e instanceof PsiVariable) {
            if (e instanceof PsiParameter) {
              final PsiParameter p = (PsiParameter)e;
              final PsiElement declarationScope = p.getDeclarationScope();
              final PsiMethod method = declarationScope instanceof PsiMethod ? (PsiMethod)declarationScope : null;
              final PsiParameterList parameterList = method == null ? null : method.getParameterList();
              // don't check catchblock parameters & etc.
              if (!(parameterList == null || parameterList != e.getParent()) &&
                  areThereInjectionsWithName(method.getName(), false)) {
                final int parameterIndex = parameterList.getParameterIndex((PsiParameter)e);
                process((PsiModifierListOwner)e, method, parameterIndex);
              }
            }
            visitVariable((PsiVariable)e);
          }
          return !myShouldStop;
        }

        private boolean processCommentInjection(@NotNull PsiElement anchor) {
          Ref<PsiElement> causeRef = Ref.create();
          BaseInjection injection = mySupport.findCommentInjection(anchor, causeRef);
          if (injection != null) {
            PsiVariable variable = PsiTreeUtil.getParentOfType(anchor, PsiVariable.class);
            visitVariableUsages(variable);
            return processCommentInjectionInner(causeRef.get(), injection);
          }
          return true;
        }
      };
      MyAnnoVisitor visitor = new MyAnnoVisitor();
      if (!visitor.processCommentInjection(firstOperand)) {
        return;
      }
      while (!places.isEmpty() && !myShouldStop) {
        final PsiElement curPlace = places.removeFirst();
        AnnotationUtilEx.visitAnnotatedElements(curPlace, visitor);
      }
    }

    protected boolean processCommentInjectionInner(PsiElement comment, BaseInjection injection) {
      processInjectionWithContext(injection, false);
      return false;
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

    private boolean processAnnotationInjections(final PsiModifierListOwner annoElement) {
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
        AnnotationUtilEx.getAnnotationFrom(annoElement, myConfiguration.getAdvancedConfiguration().getLanguageAnnotationPair(), true);
      if (annotations.length > 0) {
        return processAnnotationInjectionInner(annoElement, annotations);
      }
      return true;
    }

    protected boolean processAnnotationInjectionInner(PsiModifierListOwner owner, PsiAnnotation[] annotations) {
      final String id = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
      final String prefix = AnnotationUtilEx.calcAnnotationValue(annotations, "prefix");
      final String suffix = AnnotationUtilEx.calcAnnotationValue(annotations, "suffix");
      final BaseInjection injection = new BaseInjection(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID);
      if (prefix != null) injection.setPrefix(prefix);
      if (suffix != null) injection.setSuffix(suffix);
      if (id != null) injection.setInjectedLanguageId(id);
      processInjectionWithContext(injection, false);
      return false;
    }

    protected boolean processXmlInjections(BaseInjection injection, PsiModifierListOwner owner, PsiMethod method, int paramIndex) {
      processInjectionWithContext(injection, true);
      if (injection.isTerminal()) {
        return false;
      }
      return true;
    }

    protected void processInjectionInner(BaseInjection injection, boolean settingsAvailable) {
      processInjectionWithContext(injection, settingsAvailable);
    }

    private void processInjectionWithContext(BaseInjection injection, boolean settingsAvailable) {
      Language language = InjectorUtils.getLanguage(injection);
      if (language == null) return;

      final boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

      final Ref<Boolean> unparsableRef = Ref.create(myUnparsable);
      final List<Object> objects = ContextComputationProcessor.collectOperands(injection.getPrefix(), injection.getSuffix(), unparsableRef, myOperands);
      if (objects.isEmpty()) return;
      final List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result =
        new ArrayList<>();
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
            TextRange textRange = ElementManipulators.getManipulator(curHost).getRangeInElement(curHost);
            TextRange.assertProperRange(textRange, injection);
            result.add(Trinity.create(curHost, InjectedLanguage.create(injection.getInjectedLanguageId(), curPrefix, curSuffix, true),
                                      textRange));
          }
          else {
            final List<TextRange> injectedArea = injection.getInjectedArea(curHost);
            for (int j = 0, injectedAreaSize = injectedArea.size(); j < injectedAreaSize; j++) {
              TextRange textRange = injectedArea.get(j);
              TextRange.assertProperRange(textRange, injection);
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
            processInjection(language, Collections.singletonList(trinity), settingsAvailable, false);
          }
        }
        else {
          processInjection(language, result, settingsAvailable, unparsableRef.get());
        }
      }
    }

    protected void processInjection(Language language,
                                    List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list,
                                    boolean xmlInjection,
                                    boolean unparsable) {
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
    else if (parent instanceof PsiPolyadicExpression) {
      return true;
    }
    return false;
  }


  public Collection<String> getAnnotatedElementsValue() {
    // note: external annotations not supported
    return InjectionCache.getInstance(myProject).getAnnoIndex();
  }

  private Collection<String> getXmlAnnotatedElementsValue() {
    return InjectionCache.getInstance(myProject).getXmlIndex();
  }
}
