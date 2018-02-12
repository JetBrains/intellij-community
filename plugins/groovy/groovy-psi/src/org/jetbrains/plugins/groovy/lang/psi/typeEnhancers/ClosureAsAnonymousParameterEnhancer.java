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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.sam.SamConversionKt;

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
        expectedTypes = GroovyExpectedTypesProvider.getDefaultExpectedTypes(closure);
      }
    }
    else {
      expectedTypes = GroovyExpectedTypesProvider.getDefaultExpectedTypes(closure);
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
}
