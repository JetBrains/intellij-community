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
package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.lang.Language;
import com.intellij.lang.injection.ConcatenationAwareInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.java.InjectionCache;
import org.intellij.plugins.intelliLang.inject.java.JavaLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Max Medvedev on 9/9/13
 */
public class GrConcatenationAwareInjector implements ConcatenationAwareInjector {
  private final LanguageInjectionSupport mySupport;
  private final Configuration myConfiguration;
  private final Project myProject;

  @SuppressWarnings("UnusedParameters")
  public GrConcatenationAwareInjector(Configuration configuration, Project project, TemporaryPlacesRegistry temporaryPlacesRegistry) {
    myConfiguration = configuration;
    myProject = project;
    mySupport = InjectorUtils.findNotNullInjectionSupport(GroovyLanguageInjectionSupport.GROOVY_SUPPORT_ID);
  }

  @Override
  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement... operands) {
    if (operands.length == 0) return;

    final PsiFile file = operands[0].getContainingFile();

    if (!(file instanceof GroovyFileBase)) return;

    new InjectionProcessor(myConfiguration, mySupport, operands) {
      @Override
      protected void processInjection(Language language,
                                      List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list,
                                      boolean settingsAvailable,
                                      boolean unparsable) {
        InjectorUtils.registerInjection(language, list, file, registrar);
        InjectorUtils.registerSupport(mySupport, settingsAvailable, registrar);
        InjectorUtils.putInjectedFileUserData(registrar, InjectedLanguageUtil.FRANKENSTEIN_INJECTION, unparsable);
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

  @NotNull
  private static String getStringPresentation(@Nullable PsiElement operand) {
    if (operand instanceof GrStringInjection) {
      return operand.getText();
    }
    return "missingValue";
  }

  static class InjectionProcessor {
    private final Configuration myConfiguration;
    private final LanguageInjectionSupport mySupport;
    private final PsiElement[] myOperands;
    private boolean myShouldStop;
    private boolean myUnparsable;

    public InjectionProcessor(@NotNull Configuration configuration, @NotNull LanguageInjectionSupport support, @NotNull PsiElement... operands) {
      myConfiguration = configuration;
      mySupport = support;
      myOperands = operands;
    }

    void processInjections() {
      final PsiElement firstOperand = myOperands[0];
      final PsiElement topBlock = ControlFlowUtils.findControlFlowOwner(firstOperand);
      final LocalSearchScope searchScope = new LocalSearchScope(new PsiElement[]{topBlock instanceof GrCodeBlock
                                                                                 ? topBlock : firstOperand.getContainingFile()}, "", true);
      final THashSet<PsiModifierListOwner> visitedVars = new THashSet<>();
      final LinkedList<PsiElement> places = new LinkedList<>();
      places.add(firstOperand);
      final GrInjectionUtil.AnnotatedElementVisitor visitor = new GrInjectionUtil.AnnotatedElementVisitor() {
        @Override
        public boolean visitMethodParameter(GrExpression expression, GrCall methodCall) {
          final GrArgumentList list = methodCall.getArgumentList();
          assert list != null;

          final String methodName;
          if (methodCall instanceof GrMethodCall) {
            GrExpression invoked = ((GrMethodCall)methodCall).getInvokedExpression();
            final String referenceName = invoked instanceof GrReferenceExpression? ((GrReferenceExpression)invoked).getReferenceName() : null;
            if ("super".equals(referenceName) || "this".equals(referenceName)) { // constructor call
              final PsiClass psiClass = PsiTreeUtil.getParentOfType(methodCall, PsiClass.class, true);
              final PsiClass psiTargetClass = "super".equals(referenceName)? psiClass == null ? null : psiClass.getSuperClass() : psiClass;
              methodName = psiTargetClass == null? null : psiTargetClass.getName();
            }
            else {
              methodName = referenceName;
            }
          }
          else if (methodCall instanceof GrNewExpression) {
            final GrCodeReferenceElement classRef = ((GrNewExpression)methodCall).getReferenceElement();
            methodName = classRef == null ? null : classRef.getReferenceName();
          }
          else {
            methodName = null;
          }
          if (methodName != null && areThereInjectionsWithName(methodName, false)) {
            final GroovyResolveResult result = methodCall.advancedResolve();
            PsiElement element = result.getElement();
            if (element instanceof PsiMethod) {
              PsiMethod method = (PsiMethod)element;
              final PsiParameter[] parameters = method.getParameterList().getParameters();
              int index = GrInjectionUtil.findParameterIndex(expression, methodCall);
              if (index >= 0) {
                process(parameters[index], method, index);
              }
            }
          }
          return false;
        }

        @Override
        public boolean visitMethodReturnStatement(GrReturnStatement parent, PsiMethod method) {
          if (areThereInjectionsWithName(method.getName(), false)) {
            process(method, method, -1);
          }
          return false;
        }

        @Override
        public boolean visitVariable(PsiVariable variable) {
          if (myConfiguration.getAdvancedConfiguration().getDfaOption() != Configuration.DfaOption.OFF && visitedVars.add(variable)) {
            ReferencesSearch.search(variable, searchScope).forEach(psiReference -> {
              final PsiElement element = psiReference.getElement();
              if (element instanceof GrExpression) {
                final GrExpression refExpression = (GrExpression)element;
                places.add(refExpression);
                if (!myUnparsable) {
                  myUnparsable = checkUnparsableReference(refExpression);
                }
              }
              return true;
            });
          }
          if (!processCommentInjections(variable)) {
            myShouldStop = true;
          }
          else if (areThereInjectionsWithName(variable.getName(), false)) {
            process(variable, null, -1);
          }
          return false;
        }

        @Override
        public boolean visitAnnotationParameter(GrAnnotationNameValuePair nameValuePair, PsiAnnotation psiAnnotation) {
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

        @Override
        public boolean visitReference(GrReferenceExpression expression) {
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
      };

      while (!places.isEmpty() && !myShouldStop) {
        final PsiElement curPlace = places.removeFirst();
        GrInjectionUtil.visitAnnotatedElements(curPlace, visitor);
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
        GrConcatenationInjector.getAnnotationFrom(annoElement, myConfiguration.getAdvancedConfiguration().getLanguageAnnotationPair(), true, true);
      if (annotations.length > 0) {
        return processAnnotationInjectionInner(annoElement, annotations);
      }
      return true;
    }


    private static boolean checkUnparsableReference(GrExpression expression) {
      final PsiElement parent = expression.getParent();
      if (parent instanceof GrAssignmentExpression) {
        final GrAssignmentExpression assignmentExpression = (GrAssignmentExpression)parent;
        final IElementType operation = assignmentExpression.getOperationTokenType();
        if (assignmentExpression.getLValue() == expression && operation == GroovyTokenTypes.mPLUS_ASSIGN ) {
          return true;
        }
      }
      else if (parent instanceof GrBinaryExpression) {
        return true;
      }
      return false;

    }

    protected boolean areThereInjectionsWithName(String methodName, boolean annoOnly) {
      return true;
    }

    protected boolean processCommentInjectionInner(PsiVariable owner, PsiElement comment, BaseInjection injection) {
      processInjectionWithContext(injection, false);
      return false;
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

    private boolean processCommentInjections(PsiVariable owner) {
      Ref<PsiElement> causeRef = Ref.create();
      PsiElement anchor = owner.getFirstChild() instanceof PsiComment?
                          (owner.getModifierList() != null? owner.getModifierList() : owner.getTypeElement()) : owner;
      if (anchor == null) return true;
      BaseInjection injection = mySupport.findCommentInjection(anchor, causeRef);
      return injection == null || processCommentInjectionInner(owner, causeRef.get(), injection);
    }


    private void processInjectionWithContext(BaseInjection injection, boolean settingsAvailable) {
      Language language = InjectorUtils.getLanguage(injection);
      if (language == null) return;

      String languageID = language.getID();
      List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list = ContainerUtil.newArrayList();

      boolean unparsable = false;

      String prefix = "";
      //String suffix = "";
      for (int i = 0; i < myOperands.length; i++) {
        PsiElement operand = myOperands[i];
        final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(operand);
        if (manipulator == null) {
          unparsable = true;
          prefix += getStringPresentation(operand);
          if (i == myOperands.length - 1) {
            Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange> last = ContainerUtil.getLastItem(list);
            assert last != null;
            InjectedLanguage injected = last.second;
            list.set(list.size() - 1, Trinity.create(last.first, InjectedLanguage.create(injected.getID(), injected.getPrefix(), prefix, false), last.third));
          }
        }
        else {
          InjectedLanguage injectedLanguage = InjectedLanguage.create(languageID, prefix, "", false);
          TextRange range = manipulator.getRangeInElement(operand);
          PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)operand;
          list.add(Trinity.create(host, injectedLanguage, range));
          prefix = "";
        }
      }

      if (!list.isEmpty()) {
        processInjection(language, list, settingsAvailable, unparsable);
      }
    }

    protected void processInjection(Language language,
                                    List<Trinity<PsiLanguageInjectionHost, InjectedLanguage, TextRange>> list,
                                    boolean settingsAvailable, boolean unparsable) {
    }
  }

  public Collection<String> getAnnotatedElementsValue() {
    // note: external annotations not supported
    return InjectionCache.getInstance(myProject).getAnnoIndex();
  }

  private Collection<String> getXmlAnnotatedElementsValue() {
    return InjectionCache.getInstance(myProject).getXmlIndex();
  }
}
