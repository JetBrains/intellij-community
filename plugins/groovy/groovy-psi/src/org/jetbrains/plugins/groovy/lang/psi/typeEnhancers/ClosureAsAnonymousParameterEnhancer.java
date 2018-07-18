// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.MethodCandidate;
import org.jetbrains.plugins.groovy.lang.sam.SamConversionKt;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class ClosureAsAnonymousParameterEnhancer extends AbstractClosureParameterEnhancer {

  @Nullable
  @Override
  protected PsiType getClosureParameterType(GrClosableBlock closure, int index) {
    List<PsiType> expectedTypes;

    if (closure.getParent() instanceof GrSafeCastExpression) {
      GrSafeCastExpression safeCastExpression = (GrSafeCastExpression)closure.getParent();
      GrTypeElement typeElement = safeCastExpression.getCastTypeElement();
      if (typeElement != null) {
        PsiType castType = typeElement.getType();
        expectedTypes = ContainerUtil.newArrayList(GroovyExpectedTypesProvider.getDefaultExpectedTypes(safeCastExpression));
        for (Iterator<PsiType> iterator = expectedTypes.iterator(); iterator.hasNext(); ) {
          if (!TypesUtil.isAssignable(iterator.next(), castType, closure)) {
            iterator.remove();
          }
        }

        if (expectedTypes.isEmpty()) expectedTypes.add(castType);
      }
      else {
        expectedTypes = fromMethodCall(closure);
      }
    }
    else {
      expectedTypes = fromMethodCall(closure);
    }

    for (PsiType constraint : expectedTypes) {
      if (!(constraint instanceof PsiClassType)) continue;

      PsiClassType.ClassResolveResult result = ((PsiClassType)constraint).resolveGenerics();
      PsiClass resolved = result.getElement();
      if (resolved == null) continue;

      MethodSignature sam = SamConversionKt.findSingleAbstractSignature(resolved);
      if (sam == null) continue;

      PsiType[] parameterTypes = sam.getParameterTypes();
      if (index >= parameterTypes.length) continue;

      final PsiType suggestion = result.getSubstitutor().substitute(parameterTypes[index]);
      if (suggestion == null) continue;

      if (GroovyConfigUtils.getInstance().isVersionAtLeast(closure, GroovyConfigUtils.GROOVY2_3)) {
        if (suggestion instanceof PsiWildcardType && ((PsiWildcardType)suggestion).isSuper()) {
          return ((PsiWildcardType)suggestion).getBound();
        }
      }

      return TypesUtil.substituteAndNormalizeType(suggestion, result.getSubstitutor(), null, closure);
    }

    return null;
  }

  List<PsiType> fromMethodCall(GrClosableBlock closure) {
    GrMethodCall call = findCall(closure);
    if (call == null) return Collections.emptyList();
    GroovyResolveResult variant = call.advancedResolve();
    if (variant instanceof GroovyMethodResult) {
      MethodCandidate candidate = ((GroovyMethodResult)variant).getCandidate();
      if (candidate != null) {
        Pair<PsiParameter, PsiType> pair = candidate.mapArguments().get(new Argument(null, closure));
        if (pair != null) {
          GrReferenceExpression invokedExpression = (GrReferenceExpression)call.getInvokedExpression();
          GroovyInferenceSession session =
            new GroovyInferenceSessionBuilder(invokedExpression, candidate).startFromTop(true).resolveMode(true).build();
          PsiSubstitutor substitutor = session.inferSubst(invokedExpression);

          return Collections.singletonList(substitutor.substitute(pair.getSecond()));
        }
      }
    }
    return Collections.emptyList();

  }
}
