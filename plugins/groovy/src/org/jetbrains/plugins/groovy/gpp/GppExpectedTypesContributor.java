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
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

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
    if (parent instanceof GrNamedArgument) {
      final PsiElement map = parent.getParent();
      if (map instanceof GrListOrMap && "super".equals(((GrNamedArgument)parent).getLabelName())) {
        //todo expected property types
        return addExpectedConstructorParameters((GrListOrMap)map, new GrExpression[]{expression}, expression);
      }
    }
    return Collections.emptyList();
  }

  private static List<TypeConstraint> addExpectedConstructorParameters(GrListOrMap list,
                                                                       GrExpression[] args,
                                                                       GrExpression arg) {
    PsiType[] argTypes = ContainerUtil.map2Array(args, PsiType.class, new NullableFunction<GrExpression, PsiType>() {
      @Override
      public PsiType fun(GrExpression grExpression) {
        return grExpression.getType();
      }
    });

    final ArrayList<TypeConstraint> result = new ArrayList<TypeConstraint>();
    for (PsiType type : GroovyExpectedTypesProvider.getDefaultExpectedTypes(list)) {
      if (type instanceof PsiClassType) {
        for (GroovyResolveResult resolveResult : PsiUtil.getConstructorCandidates((PsiClassType)type, argTypes, list)) {
          final PsiElement method = resolveResult.getElement();
          if (method instanceof PsiMethod && ((PsiMethod)method).isConstructor()) {
            final Map<GrExpression,Pair<PsiParameter,PsiType>> map = GrClosureSignatureUtil
              .mapArgumentsToParameters(resolveResult, list, false, GrNamedArgument.EMPTY_ARRAY, args, GrClosableBlock.EMPTY_ARRAY);
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
