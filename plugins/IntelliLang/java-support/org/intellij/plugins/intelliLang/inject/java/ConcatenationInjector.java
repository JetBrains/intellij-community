/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
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

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement... operands) {
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
    Language tempLanguage = tempInjectedLanguage == null ? null : tempInjectedLanguage.getLanguage();
    PsiFile finalContainingFile = containingFile;
    InjectionProcessor injectionProcessor = new InjectionProcessor(myConfiguration, mySupport, operands) {
      @Override
      protected Pair<PsiLanguageInjectionHost, Language> processInjection(Language language,
                                                                          List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list,
                                                                          boolean settingsAvailable,
                                                                          boolean unparsable) {
        InjectorUtils.registerInjection(language, list, finalContainingFile, registrar);
        InjectorUtils.registerSupport(mySupport, settingsAvailable, list.get(0).getFirst(), language);
        PsiLanguageInjectionHost host = list.get(0).getFirst();
        InjectorUtils.putInjectedFileUserData(host, language, InjectedLanguageUtil.FRANKENSTEIN_INJECTION, unparsable ? Boolean.TRUE : null);
        return Pair.create(host,language);
      }

      @Override
      protected boolean areThereInjectionsWithName(String methodName, boolean annoOnly) {
        if (methodName == null) return false;
        if (getAnnotatedElementsValue().contains(methodName)) {
          return true;
        }
        return !annoOnly && getXmlAnnotatedElementsValue().contains(methodName);
      }
    };
    if (tempLanguage != null) {
      BaseInjection baseInjection = new BaseInjection(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID);
      baseInjection.setInjectedLanguageId(tempInjectedLanguage.getID());
      List<Pair<PsiLanguageInjectionHost, Language>> list = injectionProcessor.processInjectionWithContext(baseInjection, false);
      for (Pair<PsiLanguageInjectionHost, Language> pair : list) {
        PsiLanguageInjectionHost host = pair.getFirst();
        Language language = pair.getSecond();
        InjectorUtils.putInjectedFileUserData(host, language, LanguageInjectionSupport.TEMPORARY_INJECTED_LANGUAGE, tempInjectedLanguage);
      }
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

    InjectionProcessor(Configuration configuration, LanguageInjectionSupport support, PsiElement... operands) {
      myConfiguration = configuration;
      mySupport = support;
      myOperands = operands;
    }

    public void processInjections() {
      PsiElement firstOperand = myOperands[0];
      PsiElement topBlock = PsiUtil.getTopLevelEnclosingCodeBlock(firstOperand, null);
      LocalSearchScope searchScope = new LocalSearchScope(new PsiElement[]{topBlock instanceof PsiCodeBlock
                                                                                 ? topBlock : firstOperand.getContainingFile()}, "", true);
      List<PsiElement> places = new ArrayList<>(5);
      places.add(firstOperand);
      Set<PsiModifierListOwner> visitedVars = new THashSet<>();
      class MyAnnoVisitor implements AnnotationUtilEx.AnnotatedElementVisitor {
        @Override
        public boolean visitMethodParameter(PsiExpression expression, PsiCall psiCallExpression) {
          PsiExpressionList list = psiCallExpression.getArgumentList();
          assert list != null;
          int index = ArrayUtil.indexOf(list.getExpressions(), expression);
          String methodName;
          if (psiCallExpression instanceof PsiMethodCallExpression) {
            String referenceName = ((PsiMethodCallExpression)psiCallExpression).getMethodExpression().getReferenceName();
            if ("super".equals(referenceName) || "this".equals(referenceName)) { // constructor call
              PsiClass psiClass = PsiTreeUtil.getParentOfType(psiCallExpression, PsiClass.class, true);
              PsiClass psiTargetClass = "super".equals(referenceName)? psiClass == null ? null : psiClass.getSuperClass() : psiClass;
              methodName = psiTargetClass == null? null : psiTargetClass.getName();
            }
            else {
              methodName = referenceName;
            }
          }
          else if (psiCallExpression instanceof PsiNewExpression) {
            PsiJavaCodeReferenceElement classRef = ((PsiNewExpression)psiCallExpression).getClassOrAnonymousClassReference();
            methodName = classRef == null ? null : classRef.getReferenceName();
          }
          else if (psiCallExpression instanceof PsiEnumConstant) {
            PsiMethod method = psiCallExpression.resolveMethod();
            methodName = method != null ? method.getName() : null;
          }
          else {
            methodName = null;
          }
          if (methodName != null && index >= 0 && areThereInjectionsWithName(methodName, false)) {
            PsiMethod method = psiCallExpression.resolveMethod();
            if (method != null) {
              PsiParameter[] parameters = method.getParameterList().getParameters();
              if (index < parameters.length) {
                process(parameters[index], method, index);
              }
              else if (method.isVarArgs()) {
                process(parameters[parameters.length - 1], method, parameters.length - 1);
              }
            }
          }
          return false;
        }

        @Override
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
              PsiElement element = psiReference.getElement();
              if (element instanceof PsiExpression) {
                PsiExpression refExpression = (PsiExpression)element;
                places.add(refExpression);
                if (!myUnparsable) {
                  myUnparsable = checkUnparsableReference(refExpression);
                }
              }
              return true;
            });
          }
        }

        @Override
        public boolean visitVariable(PsiVariable variable) {
          visitVariableUsages(variable);
          PsiElement anchor = !(variable.getFirstChild() instanceof PsiComment) ? variable :
                              variable.getModifierList() != null ? variable.getModifierList() :
                              variable.getTypeElement();

          if (anchor != null && !processCommentInjection(anchor)) {
            myShouldStop = true;
          }
          else {
            process(variable, null, -1);
          }
          return false;
        }

        @Override
        public boolean visitAnnotationParameter(PsiNameValuePair nameValuePair, PsiAnnotation psiAnnotation) {
          String paramName = nameValuePair.getName();
          String methodName = paramName != null ? paramName : PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME;
          if (areThereInjectionsWithName(methodName, false)) {
            PsiReference reference = nameValuePair.getReference();
            PsiElement element = reference == null ? null : reference.resolve();
            if (element instanceof PsiMethod) {
              process((PsiMethod)element, (PsiMethod)element, -1);
            }
          }
          return false;
        }

        @Override
        public boolean visitReference(PsiReferenceExpression expression) {
          if (myConfiguration.getAdvancedConfiguration().getDfaOption() == Configuration.DfaOption.OFF) return true;
          PsiElement e = expression.resolve();
          if (e instanceof PsiVariable) {
            if (e instanceof PsiParameter) {
              PsiParameter p = (PsiParameter)e;
              PsiElement declarationScope = p.getDeclarationScope();
              PsiMethod method = declarationScope instanceof PsiMethod ? (PsiMethod)declarationScope : null;
              PsiParameterList parameterList = method == null ? null : method.getParameterList();
              // don't check catchblock parameters & etc.
              if (!(parameterList == null || parameterList != e.getParent()) &&
                  areThereInjectionsWithName(method.getName(), false)) {
                int parameterIndex = parameterList.getParameterIndex((PsiParameter)e);
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
      }
      MyAnnoVisitor visitor = new MyAnnoVisitor();
      if (!visitor.processCommentInjection(firstOperand)) {
        return;
      }
      while (!places.isEmpty() && !myShouldStop) {
        PsiElement curPlace = places.remove(0);
        AnnotationUtilEx.visitAnnotatedElements(curPlace, visitor);
      }
    }

    protected boolean processCommentInjectionInner(PsiElement comment, BaseInjection injection) {
      processInjectionWithContext(injection, false);
      return false;
    }

    private void process(PsiModifierListOwner owner, PsiMethod method, int paramIndex) {
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

    private boolean processAnnotationInjections(PsiModifierListOwner annoElement) {
      if (annoElement instanceof PsiParameter) {
        PsiElement scope = ((PsiParameter)annoElement).getDeclarationScope();
        if (scope instanceof PsiMethod && !areThereInjectionsWithName(((PsiNamedElement)scope).getName(), true)) {
          return true;
        }
      }
      PsiAnnotation[] annotations =
        AnnotationUtilEx.getAnnotationFrom(annoElement, myConfiguration.getAdvancedConfiguration().getLanguageAnnotationPair(), true);
      if (annotations.length > 0) {
        return processAnnotationInjectionInner(annoElement, annotations);
      }
      return true;
    }

    protected boolean processAnnotationInjectionInner(PsiModifierListOwner owner, PsiAnnotation[] annotations) {
      String id = AnnotationUtilEx.calcAnnotationValue(annotations, "value");
      String prefix = AnnotationUtilEx.calcAnnotationValue(annotations, "prefix");
      String suffix = AnnotationUtilEx.calcAnnotationValue(annotations, "suffix");
      BaseInjection injection = new BaseInjection(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID);
      if (prefix != null) injection.setPrefix(prefix);
      if (suffix != null) injection.setSuffix(suffix);
      if (id != null) injection.setInjectedLanguageId(id);
      processInjectionWithContext(injection, false);
      return false;
    }

    protected boolean processXmlInjections(BaseInjection injection, PsiModifierListOwner owner, PsiMethod method, int paramIndex) {
      processInjectionWithContext(injection, true);
      return !injection.isTerminal();
    }

    @NotNull
    List<Pair<PsiLanguageInjectionHost, Language>> processInjectionWithContext(BaseInjection injection, boolean settingsAvailable) {
      Language language = InjectorUtils.getLanguage(injection);
      if (language == null) return Collections.emptyList();

      boolean separateFiles = !injection.isSingleFile() && StringUtil.isNotEmpty(injection.getValuePattern());

      Ref<Boolean> unparsableRef = Ref.create(myUnparsable);
      List<Object> objects = ContextComputationProcessor.collectOperands(injection.getPrefix(), injection.getSuffix(), unparsableRef, myOperands);
      if (objects.isEmpty()) return Collections.emptyList();
      List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> result = new ArrayList<>();
      int len = objects.size();
      for (int i = 0; i < len; i++) {
        String curPrefix = null;
        Object o = objects.get(i);
        if (o instanceof String) {
          curPrefix = (String)o;
          if (i == len - 1) return Collections.emptyList(); // IDEADEV-26751
          o = objects.get(++i);
        }
        String curSuffix = null;
        PsiLanguageInjectionHost curHost = null;
        if (o instanceof PsiLanguageInjectionHost) {
          curHost = (PsiLanguageInjectionHost)o;
          if (i == len - 2) {
            Object next = objects.get(i + 1);
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
          if (curHost instanceof PsiLiteralExpression) {
            List<TextRange> injectedArea = injection.getInjectedArea(curHost);
            for (int j = 0, injectedAreaSize = injectedArea.size(); j < injectedAreaSize; j++) {
              TextRange textRange = injectedArea.get(j);
              TextRange.assertProperRange(textRange, injection);
              result.add(Trinity.create(
                curHost, InjectedLanguage.create(injection.getInjectedLanguageId(),
                                                 separateFiles || j == 0 ? curPrefix : "",
                                                 separateFiles || j == injectedAreaSize - 1 ? curSuffix : "",
                                                 true), textRange));
            }
          }
          else {
            TextRange textRange = ElementManipulators.getManipulator(curHost).getRangeInElement(curHost);
            TextRange.assertProperRange(textRange, injection);
            result.add(Trinity.create(curHost, InjectedLanguage.create(injection.getInjectedLanguageId(), curPrefix, curSuffix, true),
                                      textRange));
          }
        }
      }
      if (result.isEmpty()) {
        return Collections.emptyList();
      }
      List<Pair<PsiLanguageInjectionHost, Language>> res = new ArrayList<>();
      if (separateFiles) {
        for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
          ContainerUtil.addIfNotNull(res, processInjection(language, Collections.singletonList(trinity), settingsAvailable, false));
        }
      }
      else {
        if (isReferenceInject(language)) {
          // OMG in case of reference inject they confused shreds (several places in the host file to form a single injection) with several injections
          for (Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> trinity : result) {
            ContainerUtil.addIfNotNull(res, processInjection(language, Collections.singletonList(trinity), settingsAvailable, unparsableRef.get()));
          }
        }
        else {
          ContainerUtil.addIfNotNull(res, processInjection(language, result, settingsAvailable, unparsableRef.get()));
        }
      }
      return res;
    }

    private static boolean isReferenceInject(Language language) {
      return LanguageParserDefinitions.INSTANCE.forLanguage(language) == null && ReferenceInjector.findById(language.getID()) != null;
    }

    protected Pair<PsiLanguageInjectionHost, Language> processInjection(Language language,
                                                                        List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list,
                                                                        boolean xmlInjection,
                                                                        boolean unparsable) {
      return null;
    }

    protected boolean areThereInjectionsWithName(String methodName, boolean annoOnly) {
      return true;
    }
  }

  private static boolean checkUnparsableReference(PsiExpression refExpression) {
    PsiElement parent = refExpression.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      IElementType operation = assignmentExpression.getOperationTokenType();
      if (assignmentExpression.getLExpression() == refExpression && JavaTokenType.PLUSEQ.equals(operation)) {
        return true;
      }
    }
    else if (parent instanceof PsiPolyadicExpression || 
             parent instanceof PsiParenthesizedExpression ||
             parent instanceof PsiConditionalExpression) {
      return true;
    }
    return false;
  }


  private Collection<String> getAnnotatedElementsValue() {
    // note: external annotations not supported
    return InjectionCache.getInstance(myProject).getAnnoIndex();
  }

  private Collection<String> getXmlAnnotatedElementsValue() {
    return InjectionCache.getInstance(myProject).getXmlIndex();
  }
}
