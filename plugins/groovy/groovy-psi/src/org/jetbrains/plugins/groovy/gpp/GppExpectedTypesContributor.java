// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesContributor;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
final class GppExpectedTypesContributor extends GroovyExpectedTypesContributor {
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

        return addExpectedConstructorParameters(list, expression);
      }
    }
    return Collections.emptyList();
  }

  private static List<TypeConstraint> addExpectedConstructorParameters(GrListOrMap list, GrExpression arg) {
    GroovyConstructorReference reference = list.getConstructorReference();
    if (reference == null) {
      return Collections.emptyList();
    }
    GrExpression[] args = list.getInitializers();
    List<TypeConstraint> result = new ArrayList<>();
    for (GroovyResolveResult constructorResult : reference.resolve(false)) {
      final Map<GrExpression, Pair<PsiParameter, PsiType>> map = GrClosureSignatureUtil.mapArgumentsToParameters(
        constructorResult, list, false, true, GrNamedArgument.EMPTY_ARRAY, args, GrClosableBlock.EMPTY_ARRAY
      );
      if (map == null) {
        continue;
      }
      final Pair<PsiParameter, PsiType> pair = map.get(arg);
      if (pair == null) {
        continue;
      }
      result.add(SubtypeConstraint.create(pair.second));
    }
    return result;
  }
}
