// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.PlatformUtils;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.java.JavaLanguageInjectionSupport;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public final class GrConcatenationInjector implements MultiHostInjector {
  public GrConcatenationInjector() {
    if ("AndroidStudio".equals(PlatformUtils.getPlatformPrefix())) {
      // fix https://code.google.com/p/android/issues/detail?id=201624
      throw ExtensionNotApplicableException.create();
    }
  }

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    assert context instanceof GrLiteral;
    final GrLiteral literal = (GrLiteral)context;
    if (!literal.isValidHost()) return;

    processInPlace(registrar, literal);
  }

  public static void processInPlace(MultiHostRegistrar registrar, GrLiteral literal) {
    BaseInjection injection = findLanguageParams(literal);

    if (injection != null) {
      LanguageInjectionSupport support = InjectorUtils.findInjectionSupport(GroovyLanguageInjectionSupport.GROOVY_SUPPORT_ID);
      InjectorUtils.registerInjectionSimple(literal, injection, support, registrar);
    }
  }

  private static @Nullable BaseInjection findLanguageParams(@NotNull PsiElement place) {
    PsiElement parent = place.getParent();
    if (parent instanceof GrAssignmentExpression && ((GrAssignmentExpression)parent).getRValue() == place) {
      final GrExpression lvalue = ((GrAssignmentExpression)parent).getLValue();
      if (lvalue instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)lvalue).resolve();
        if (resolved instanceof PsiModifierListOwner) {
          return getLanguageParams((PsiModifierListOwner)resolved);
        }
      }
    }
    else if (parent instanceof GrVariable) {
      return getLanguageParams((PsiModifierListOwner)parent);
    }
    else if (parent instanceof GrBinaryExpression expression) {
      PsiMethod method = GrInjectionUtil.getMethodFromLeftShiftOperator(expression);
      PsiParameter parameter = GrInjectionUtil.getSingleParameterFromMethod(method);
      return parameter != null ? getLanguageParams(parameter) : null;
    }
    else if (parent instanceof GrArgumentList) {
      final PsiElement pparent = parent.getParent();

      if (pparent instanceof GrCall call) {
        final GroovyResolveResult result = call.advancedResolve();
        if (result.getElement() != null) {
          final Map<GrExpression, Pair<PsiParameter, PsiType>> map =
            GrClosureSignatureUtil
              .mapArgumentsToParameters(result, place, false, false, call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments());

          if (map != null) {
            final Pair<PsiParameter, PsiType> pair = map.get(place);
            return getLanguageParams(pair.first);
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static BaseInjection getLanguageParams(PsiModifierListOwner annotationOwner) {
    return CachedValuesManager.getCachedValue(annotationOwner, () ->
      CachedValueProvider.Result.create(calcLanguageParams(annotationOwner), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nullable
  private static BaseInjection calcLanguageParams(PsiModifierListOwner annotationOwner) {
    final Pair<String, ? extends Set<String>> pair = Configuration.getInstance().getAdvancedConfiguration().getLanguageAnnotationPair();
    final PsiAnnotation[] annotations = getAnnotationFrom(annotationOwner, pair, true, true);
    if (annotations.length > 0) {
      String prefix = StringUtil.notNullize(AnnotationUtilEx.calcAnnotationValue(annotations, "prefix"));
      String suffix = StringUtil.notNullize(AnnotationUtilEx.calcAnnotationValue(annotations, "suffix"));
      String id = StringUtil.notNullize(AnnotationUtilEx.calcAnnotationValue(annotations, "value"));
      if (!StringUtil.isEmpty(id)) {
        BaseInjection injection = new BaseInjection(GroovyLanguageInjectionSupport.GROOVY_SUPPORT_ID);
        injection.setPrefix(prefix);
        injection.setSuffix(suffix);
        injection.setInjectedLanguageId(id);
        return injection;
      }
    }

    if (annotationOwner instanceof PsiParameter && annotationOwner.getParent() instanceof PsiParameterList && annotationOwner.getParent().getParent() instanceof PsiMethod) {
      List<BaseInjection> injections = Configuration.getInstance().getInjections(JavaLanguageInjectionSupport.JAVA_SUPPORT_ID);
      for (BaseInjection injection : injections) {
        if (injection.acceptsPsiElement(annotationOwner)) {
          return injection;
        }
      }
    }

    return null;
  }

  public static PsiAnnotation @NotNull [] getAnnotationFrom(PsiModifierListOwner owner,
                                                            Pair<String, ? extends Set<String>> annotationName,
                                                            boolean allowIndirect,
                                                            boolean inHierarchy) {
    if (!isLanguageAnnotationTargetGroovy(owner)) return PsiAnnotation.EMPTY_ARRAY;

    return AnnotationUtilEx.getAnnotationsFromImpl(owner, annotationName, allowIndirect, inHierarchy);
  }

  private static boolean isLanguageAnnotationTargetGroovy(PsiModifierListOwner owner) {
    return owner instanceof GrMethod && ((GrMethod)owner).getReturnTypeElementGroovy() == null ||
           owner instanceof GrVariable && ((GrVariable)owner).getTypeElementGroovy() == null ||
           PsiUtilEx.isLanguageAnnotationTarget(owner);
  }

  @NotNull
  @Override
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(GrLiteral.class);
  }
}
