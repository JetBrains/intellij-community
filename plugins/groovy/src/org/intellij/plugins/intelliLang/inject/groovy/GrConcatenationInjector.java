/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.InjectorUtils;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
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
public class GrConcatenationInjector implements MultiHostInjector {
  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    assert context instanceof GrLiteral;
    final GrLiteral literal = (GrLiteral)context;

    final PsiElement parent = literal.getParent();
    if (parent instanceof GrAssignmentExpression && ((GrAssignmentExpression)parent).getRValue() == literal) {
      final GrExpression lvalue = ((GrAssignmentExpression)parent).getLValue();
      if (lvalue instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)lvalue).resolve();
        if (resolved instanceof PsiModifierListOwner) {
          processAnnotations(registrar, literal, (PsiModifierListOwner)resolved);
        }
      }
    }
    else if (parent instanceof GrVariable) {
      processAnnotations(registrar, literal, ((GrVariable)parent));
    }
    else if (parent instanceof GrArgumentList) {
      final PsiElement pparent = parent.getParent();

      if (pparent instanceof GrCall) {
        final GrCall call = (GrCall)pparent;
        final GroovyResolveResult result = call.advancedResolve();
        if (result.getElement() != null) {
          final Map<GrExpression, Pair<PsiParameter, PsiType>> map = GrClosureSignatureUtil
            .mapArgumentsToParameters(result, literal, false, false, call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments());

          if (map != null) {
            final Pair<PsiParameter, PsiType> pair = map.get(literal);
            processAnnotations(registrar, literal, pair.first);
          }
        }
      }
    }
  }

  private static void processAnnotations(MultiHostRegistrar registrar,
                                         PsiLanguageInjectionHost host,
                                         PsiModifierListOwner annotationOwner) {
    final Pair<String, ? extends Set<String>> pair =
      Configuration.getInstance().getAdvancedConfiguration().getLanguageAnnotationPair();

    final PsiAnnotation[] annotations = getAnnotationFrom(annotationOwner, pair, true, true);
    if (annotations.length > 0) {
      BaseInjection injection = new BaseInjection(GroovyLanguageInjectionSupport.GROOVY_SUPPORT_ID);

      injection.setPrefix(StringUtil.notNullize(AnnotationUtilEx.calcAnnotationValue(annotations, "prefix")));
      injection.setSuffix(StringUtil.notNullize(AnnotationUtilEx.calcAnnotationValue(annotations, "suffix")));
      injection.setInjectedLanguageId(StringUtil.notNullize(AnnotationUtilEx.calcAnnotationValue(annotations, "value")));

      LanguageInjectionSupport support = InjectorUtils.findInjectionSupport(GroovyLanguageInjectionSupport.GROOVY_SUPPORT_ID);
      InjectorUtils.registerInjectionSimple(host, injection, support, registrar);
    }
  }

  @NotNull
  public static PsiAnnotation[] getAnnotationFrom(PsiModifierListOwner owner,
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
