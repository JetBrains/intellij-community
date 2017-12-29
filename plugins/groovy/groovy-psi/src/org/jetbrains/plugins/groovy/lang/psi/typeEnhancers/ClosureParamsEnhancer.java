/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Collections;
import java.util.List;

public class ClosureParamsEnhancer extends AbstractClosureParameterEnhancer {

  @Nullable
  @Override
  protected PsiType getClosureParameterType(GrClosableBlock closure, int index) {
    if (!GroovyConfigUtils.getInstance().isVersionAtLeast(closure, GroovyConfigUtils.GROOVY2_3)) return null;

    final GrParameter[] parameters = closure.getAllParameters();
    if (containsParametersWithDeclaredType(parameters)) {
      return null;
    }

    List<PsiType[]> fittingSignatures = findFittingSignatures(closure);

    if (fittingSignatures.size() == 1) {
      PsiType[] expectedSignature = fittingSignatures.get(0);
      return expectedSignature[index];
    }

    return null;
  }

  @NotNull
  public static List<PsiType[]> findFittingSignatures(GrClosableBlock closure) {
    GrCall call = findCall(closure);
    if (call == null) return Collections.emptyList();

    List<PsiType[]> expectedSignatures = ContainerUtil.newArrayList();

    GroovyResolveResult[] variants = call.getCallVariants(closure);
    for (GroovyResolveResult variant : variants) {
      expectedSignatures.addAll(inferExpectedSignatures(variant, call, closure));
    }

    final GrParameter[] parameters = closure.getAllParameters();
    return ContainerUtil.findAll(expectedSignatures, types -> types.length == parameters.length);
  }

  private static List<PsiType[]> inferExpectedSignatures(@NotNull GroovyResolveResult variant, @NotNull GrCall call, @NotNull GrClosableBlock closure) {
    PsiElement element = variant.getElement();

    while (element instanceof PsiMirrorElement) element = ((PsiMirrorElement)element).getPrototype();
    if (!(element instanceof PsiMethod)) return Collections.emptyList();

    List<Pair<PsiParameter, PsiType>> params = ResolveUtil.collectExpectedParamsByArg(closure,
                                                                                      new GroovyResolveResult[]{variant},
                                                                                      call.getNamedArguments(),
                                                                                      call.getExpressionArguments(),
                                                                                      call.getClosureArguments(), closure);
    if (params.isEmpty()) return Collections.emptyList();

    Pair<PsiParameter, PsiType> pair = params.get(0);

    PsiParameter param = pair.getFirst();
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

    return signatureHintProcessor.inferExpectedSignatures((PsiMethod)element,
                                                          variant.getSubstitutor(),
                                                          SignatureHintProcessor.buildOptions(anno));
  }

  private static boolean containsParametersWithDeclaredType(GrParameter[] parameters) {
    return ContainerUtil.find(parameters, parameter -> parameter.getDeclaredType() != null) != null;
  }

  @Nullable
  private static GrCall findCall(@NotNull GrClosableBlock closure) {
    PsiElement parent = closure.getParent();
    if (parent instanceof GrCall && ArrayUtil.contains(closure, ((GrCall)parent).getClosureArguments())) {
      return (GrCall)parent;
    }

    if (parent instanceof GrArgumentList) {
      PsiElement pparent = parent.getParent();
      if (pparent instanceof GrCall) {
        return (GrCall)pparent;
      }
    }

    return null;
  }
}
