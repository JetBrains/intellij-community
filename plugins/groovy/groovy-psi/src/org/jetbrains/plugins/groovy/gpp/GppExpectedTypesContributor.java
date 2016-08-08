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
package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesContributor;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class GppExpectedTypesContributor extends GroovyExpectedTypesContributor {
  @Override
  public List<TypeConstraint> calculateTypeConstraints(@NotNull GrExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof GrListOrMap) {
      final GrListOrMap list = (GrListOrMap)parent;
      if (!list.isMap()) {
        final PsiType listType = list.getType();
        if (!(listType instanceof GrTupleType)) {
          return Collections.emptyList();
        }

        return addExpectedConstructorParameters(list, list.getInitializers(), expression);
      }
    }
    return Collections.emptyList();
  }

  private static List<TypeConstraint> addExpectedConstructorParameters(GrListOrMap list,
                                                                       GrExpression[] args,
                                                                       GrExpression arg) {
    PsiType[] argTypes = ContainerUtil.map2Array(args, PsiType.class, (NullableFunction<GrExpression, PsiType>)grExpression -> grExpression.getType());

    final ArrayList<TypeConstraint> result = new ArrayList<>();
    for (PsiType type : GroovyExpectedTypesProvider.getDefaultExpectedTypes(list)) {
      if (type instanceof PsiClassType) {
        for (GroovyResolveResult resolveResult : PsiUtil.getConstructorCandidates((PsiClassType)type, argTypes, list)) {
          final PsiElement method = resolveResult.getElement();
          if (method instanceof PsiMethod && ((PsiMethod)method).isConstructor()) {
            final Map<GrExpression,Pair<PsiParameter,PsiType>> map = GrClosureSignatureUtil
              .mapArgumentsToParameters(resolveResult, list, false, true, GrNamedArgument.EMPTY_ARRAY, args, GrClosableBlock.EMPTY_ARRAY);
            if (map != null) {
              final Pair<PsiParameter, PsiType> pair = map.get(arg);
              if (pair != null) {
                result.add(SubtypeConstraint.create(pair.second));
              }
            }
          }
        }
      }
    }
    return result;
  }
}
