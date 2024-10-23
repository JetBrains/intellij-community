// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate;
import org.jetbrains.plugins.groovy.lang.resolve.api.PsiCallParameter;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.findCall;

public final class ClosureParamsEnhancer extends AbstractClosureParameterEnhancer {
  @Nullable
  @Override
  protected PsiType getClosureParameterType(@NotNull GrFunctionalExpression expression, int index) {
    if (!GroovyConfigUtils.getInstance().isVersionAtLeast(expression, GroovyConfigUtils.GROOVY2_3)) return null;

    final GrParameter[] parameters = expression.getAllParameters();
    if (containsParametersWithDeclaredType(parameters)) {
      return null;
    }

    List<PsiType[]> fittingSignatures = findFittingSignatures(expression);

    if (fittingSignatures.size() == 1) {
      PsiType[] expectedSignature = fittingSignatures.get(0);
      return expectedSignature[index];
    }

    return null;
  }

  @NotNull
  @Unmodifiable
  public static List<PsiType[]> findFittingSignatures(@NotNull GrFunctionalExpression expression) {
    GrMethodCall call = findCall(expression);
    if (call == null) return Collections.emptyList();

    GroovyResolveResult variant = call.advancedResolve();

    List<PsiType[]> expectedSignatures = inferExpectedSignatures(variant, call, expression);

    final GrParameter[] parameters = expression.getAllParameters();
    return ContainerUtil.findAll(expectedSignatures, types -> types.length == parameters.length);
  }

  private static List<PsiType[]> inferExpectedSignatures(@NotNull GroovyResolveResult variant,
                                                         @NotNull GrMethodCall call,
                                                         @NotNull GrFunctionalExpression expression) {
    PsiElement element = variant.getElement();

    while (element instanceof PsiMirrorElement) element = ((PsiMirrorElement)element).getPrototype();
    if (!(element instanceof PsiMethod)) return Collections.emptyList();

    PsiParameter param = null;
    if (variant instanceof GroovyMethodResult) {
      GroovyMethodCandidate candidate = ((GroovyMethodResult)variant).getCandidate();
      if (candidate != null) {
        ArgumentMapping<PsiCallParameter> mapping = candidate.getArgumentMapping();
        if (mapping != null) {
          PsiCallParameter obj = mapping.targetParameter(new ExpressionArgument(expression));
          param = obj == null ? null : obj.getPsi();
        }
      }
    } else {
      List<Pair<PsiParameter, PsiType>> params = ResolveUtil.collectExpectedParamsByArg(expression, //TODO:Replace with new api
                                                                                        new GroovyResolveResult[]{variant},
                                                                                        call.getNamedArguments(),
                                                                                        call.getExpressionArguments(),
                                                                                        call.getClosureArguments(), expression);
      if (params.isEmpty()) return Collections.emptyList();

      Pair<PsiParameter, PsiType> pair = params.get(0);

      param = pair.getFirst();
    }
    if (param == null) return Collections.emptyList();

    PsiModifierList modifierList = param.getModifierList();
    if (modifierList == null) return Collections.emptyList();

    PsiAnnotation anno = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_STC_CLOSURE_PARAMS);
    if (anno == null) return Collections.emptyList();

    PsiClass closureSignatureHint = GrAnnotationUtil.inferClassAttribute(anno, "value");
    if (closureSignatureHint == null) return Collections.emptyList();

    String qnameOfClosureSignatureHint = closureSignatureHint.getQualifiedName();
    if (qnameOfClosureSignatureHint == null) return Collections.emptyList();

    SignatureHintProcessor signatureHintProcessor = SignatureHintProcessor.getHintProcessor(qnameOfClosureSignatureHint);
    if (signatureHintProcessor == null) return Collections.emptyList();

    PsiSubstitutor substitutor = null;
    if (variant instanceof GroovyMethodResult) {
      GroovyMethodCandidate candidate = ((GroovyMethodResult)variant).getCandidate();
      if (candidate != null) {
        GroovyInferenceSessionBuilder builder = new GroovyInferenceSessionBuilder(call, candidate, variant.getContextSubstitutor());
        substitutor = computeAnnotationBasedSubstitutor(call, builder);
      }
    }
    if (substitutor == null ) {
      substitutor = variant.getSubstitutor();
    }

    return signatureHintProcessor.inferExpectedSignatures((PsiMethod)element, substitutor, SignatureHintProcessor.buildOptions(anno));
  }

  @NotNull
  private static PsiSubstitutor computeAnnotationBasedSubstitutor(@NotNull GrCall call,
                                                                  @NotNull GroovyInferenceSessionBuilder builder) {
    return builder.skipClosureIn(call).resolveMode(false).build().inferSubst();
  }

  private static boolean containsParametersWithDeclaredType(GrParameter[] parameters) {
    return ContainerUtil.find(parameters, parameter -> parameter.getDeclaredType() != null) != null;
  }
}
