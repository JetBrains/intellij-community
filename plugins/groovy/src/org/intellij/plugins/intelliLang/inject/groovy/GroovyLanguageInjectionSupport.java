/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.java.JavaLanguageInjectionSupport;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteralContainer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class GroovyLanguageInjectionSupport extends AbstractLanguageInjectionSupport {
  @NonNls public static final String GROOVY_SUPPORT_ID = "groovy";

  @Override
  @NotNull
  public String getId() {
    return GROOVY_SUPPORT_ID;
  }

  @Override
  @NotNull
  public Class[] getPatternClasses() {
    return new Class[] {GroovyPatterns.class};
  }

  @Override
  public boolean isApplicableTo(PsiLanguageInjectionHost host) {
    return host instanceof GroovyPsiElement;
  }

  @Override
  public boolean useDefaultInjector(PsiLanguageInjectionHost host) {
    return true;
  }

  @Override
  public String getHelpId() {
    return "reference.settings.language.injection.groovy";
  }

  @Override
  public boolean addInjectionInPlace(@NotNull Language language, @Nullable PsiLanguageInjectionHost psiElement) {
    if (!isStringLiteral(psiElement)) return false;


    return doInject(language.getID(), psiElement, psiElement);
  }

  @Override
  public boolean removeInjectionInPlace(@Nullable final PsiLanguageInjectionHost psiElement) {
    if (!isStringLiteral(psiElement)) return false;

    GrLiteralContainer host = (GrLiteralContainer)psiElement;
    final HashMap<BaseInjection, Pair<PsiMethod, Integer>> injectionsMap = ContainerUtil.newHashMap();
    final ArrayList<PsiElement> annotations = new ArrayList<>();
    final Project project = host.getProject();
    final Configuration configuration = Configuration.getProjectInstance(project);
    collectInjections(host, configuration, this, injectionsMap, annotations);

    if (injectionsMap.isEmpty() && annotations.isEmpty()) return false;
    final ArrayList<BaseInjection> originalInjections = new ArrayList<>(injectionsMap.keySet());
    final List<BaseInjection> newInjections = ContainerUtil.mapNotNull(originalInjections,
                                                                       (NullableFunction<BaseInjection, BaseInjection>)injection -> {
                                                                         final Pair<PsiMethod, Integer> pair = injectionsMap.get(injection);
                                                                         final String placeText = JavaLanguageInjectionSupport.getPatternStringForJavaPlace(pair.first, pair.second);
                                                                         final BaseInjection newInjection = injection.copy();
                                                                         newInjection.setPlaceEnabled(placeText, false);
                                                                         return InjectorUtils.canBeRemoved(newInjection) ? null : newInjection;
                                                                       });
    configuration.replaceInjectionsWithUndo(project, newInjections, originalInjections, annotations);
    return true;
  }

  private static void collectInjections(@NotNull GrLiteralContainer host,
                                        @NotNull Configuration configuration,
                                        @NotNull LanguageInjectionSupport support,
                                        @NotNull final HashMap<BaseInjection, Pair<PsiMethod, Integer>> injectionsMap,
                                        @NotNull final ArrayList<PsiElement> annotations) {
    new GrConcatenationAwareInjector.InjectionProcessor(configuration, support, host) {
      @Override
      protected boolean processCommentInjectionInner(PsiVariable owner, PsiElement comment, BaseInjection injection) {
        ContainerUtil.addAll(annotations, comment);
        return true;
      }

      @Override
      protected boolean processAnnotationInjectionInner(PsiModifierListOwner owner, PsiAnnotation[] annos) {
        ContainerUtil.addAll(annotations, annos);
        return true;
      }

      @Override
      protected boolean processXmlInjections(BaseInjection injection, PsiModifierListOwner owner, PsiMethod method, int paramIndex) {
        injectionsMap.put(injection, Pair.create(method, paramIndex));
        return true;
      }
    }.processInjections();
  }

  private static boolean doInject(@NotNull String languageId,
                                  @NotNull PsiElement psiElement,
                                  @NotNull PsiLanguageInjectionHost host) {
    final PsiElement target = getTopLevelInjectionTarget(psiElement);
    final PsiElement parent = target.getParent();
    final Project project = psiElement.getProject();

    if (parent instanceof GrReturnStatement) {
      final GrControlFlowOwner owner = ControlFlowUtils.findControlFlowOwner(parent);
      if (owner instanceof GrOpenBlock && owner.getParent() instanceof GrMethod) {
        return JavaLanguageInjectionSupport.doInjectInJavaMethod(project, (PsiMethod)owner.getParent(), -1, host, languageId);
      }
    }
    else if (parent instanceof GrMethod) {
      return JavaLanguageInjectionSupport.doInjectInJavaMethod(project, (GrMethod)parent, -1, host, languageId);
    }
    else if (parent instanceof GrAnnotationNameValuePair) {
      final PsiReference ref = parent.getReference();
      if (ref != null) {
        final PsiElement resolved = ref.resolve();
        if (resolved instanceof PsiMethod) {
          return JavaLanguageInjectionSupport.doInjectInJavaMethod(project, (PsiMethod)resolved, -1, host, languageId);
        }
      }
    }
    else if (parent instanceof GrArgumentList && parent.getParent() instanceof GrMethodCall) {
      final PsiMethod method = ((GrMethodCall)parent.getParent()).resolveMethod();
      if (method != null) {
        final int index = GrInjectionUtil.findParameterIndex(target, ((GrMethodCall)parent.getParent()));
        if (index >= 0) {
          return JavaLanguageInjectionSupport.doInjectInJavaMethod(project, method, index, host, languageId);
        }
      }
    }
    else if (parent instanceof GrAssignmentExpression) {
      final GrExpression expr = ((GrAssignmentExpression)parent).getLValue();
      if (expr instanceof GrReferenceExpression) {
        final PsiElement element = ((GrReferenceExpression)expr).resolve();
        if (element != null) {
          return doInject(languageId, element, host);
        }
      }
    }
    else {

      if (parent instanceof PsiVariable) {
        Processor<PsiLanguageInjectionHost> fixer = getAnnotationFixer(project, languageId);
        if (JavaLanguageInjectionSupport.doAddLanguageAnnotation(project, (PsiModifierListOwner)parent, host, languageId, fixer)) return true;
      }
      else if (target instanceof PsiVariable && !(target instanceof LightElement)) {
        Processor<PsiLanguageInjectionHost> fixer = getAnnotationFixer(project, languageId);
        if (JavaLanguageInjectionSupport.doAddLanguageAnnotation(project, (PsiModifierListOwner)target, host, languageId, fixer)) return true;
      }
    }
    return false;
  }

  private static Processor<PsiLanguageInjectionHost> getAnnotationFixer(@NotNull final Project project,
                                                                        @NotNull final String languageId) {
    return host -> {
      if (host == null) return false;

      final Configuration.AdvancedConfiguration configuration = Configuration.getProjectInstance(project).getAdvancedConfiguration();
      boolean allowed = configuration.isSourceModificationAllowed();
      configuration.setSourceModificationAllowed(true);
      try {
        return doInject(languageId, host, host);
      }
      finally {
        configuration.setSourceModificationAllowed(allowed);
      }
    };
  }

  @Contract("null -> false")
  private static boolean isStringLiteral(@Nullable PsiLanguageInjectionHost element) {
    if (element instanceof GrStringContent) {
      return true;
    }
    else if (element instanceof GrLiteral) {
      final IElementType type = GrLiteralImpl.getLiteralType((GrLiteral)element);
      return TokenSets.STRING_LITERALS.contains(type);
    }
    return false;
  }

  @NotNull
  public static PsiElement getTopLevelInjectionTarget(@NotNull final PsiElement host) {
    PsiElement target = host;
    PsiElement parent = target.getParent();
    for (; parent != null; target = parent, parent = target.getParent()) {
      if (parent instanceof GrBinaryExpression) continue;
      if (parent instanceof GrString) continue;
      if (parent instanceof GrParenthesizedExpression) continue;
      if (parent instanceof GrConditionalExpression && ((GrConditionalExpression)parent).getCondition() != target) continue;
      if (parent instanceof GrAnnotationArrayInitializer) continue;
      if (parent instanceof GrListOrMap) {
        parent = parent.getParent(); continue;
      }
      break;
    }
    return target;
  }
}
