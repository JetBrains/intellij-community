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

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.AbstractClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author peter
 */
public class GppClosureParameterTypeProvider extends AbstractClosureParameterEnhancer {
  @Override
  protected PsiType getClosureParameterType(GrClosableBlock closure, int index) {
    final PsiElement parent = closure.getParent();
    if (parent instanceof GrNamedArgument) {
      final Pair<PsiMethod, PsiSubstitutor> pair = getOverriddenMethod((GrNamedArgument)parent);
      if (pair != null) {
        final PsiParameter[] parameters = pair.first.getParameterList().getParameters();
        if (parameters.length > index) {
          return pair.second.substitute(parameters[index].getType());
        }
        return null;
      }
    }

    if (parent instanceof GrListOrMap) {
      final GrListOrMap list = (GrListOrMap)parent;
      if (!list.isMap()) {
        final PsiType listType = list.getType();
        final int argIndex = Arrays.asList(list.getInitializers()).indexOf(closure);
        assert argIndex >= 0;
        if (listType instanceof GrTupleType) {
          for (PsiType type : GroovyExpectedTypesProvider.getDefaultExpectedTypes(list)) {
            if (!(type instanceof PsiClassType)) continue;

            final GroovyResolveResult[] candidates = PsiUtil.getConstructorCandidates((PsiClassType)type,((GrTupleType)listType).getComponentTypes(),closure);
            for (GroovyResolveResult resolveResult : candidates) {
              final PsiElement method = resolveResult.getElement();
              if (!(method instanceof PsiMethod) || !((PsiMethod)method).isConstructor()) continue;

              final PsiParameter[] parameters = ((PsiMethod)method).getParameterList().getParameters();
              if (parameters.length <= argIndex) continue;

              final PsiType toCastTo = resolveResult.getSubstitutor().substitute(parameters[argIndex].getType());
              final PsiType suggestion = getSingleMethodParameterType(toCastTo, index, closure);
              if (suggestion != null) return suggestion;
            }
          }
        }
        return null;
      }
    }

    for (PsiType constraint : GroovyExpectedTypesProvider.getDefaultExpectedTypes(closure)) {
      final PsiType suggestion = getSingleMethodParameterType(constraint, index, closure);
      if (suggestion != null) {
        return suggestion;
      }
    }
    return null;
  }

  @Nullable
  public static Pair<PsiMethod, PsiSubstitutor> getOverriddenMethod(GrNamedArgument namedArgument) {
    return ContainerUtil.getFirstItem(getOverriddenMethodVariants(namedArgument), null);
  }

  @NotNull
  public static List<Pair<PsiMethod, PsiSubstitutor>> getOverriddenMethodVariants(GrNamedArgument namedArgument) {

    final GrArgumentLabel label = namedArgument.getLabel();
    if (label == null) {
      return Collections.emptyList();
    }

    final String methodName = label.getName();
    if (methodName == null) {
      return Collections.emptyList();
    }

    final PsiElement map = namedArgument.getParent();
    if (map instanceof GrListOrMap && ((GrListOrMap)map).isMap()) {
      for (PsiType expected : GroovyExpectedTypesProvider.getDefaultExpectedTypes((GrExpression)map)) {
        if (expected instanceof PsiClassType) {
          final List<Pair<PsiMethod, PsiSubstitutor>> pairs = getMethodsToOverrideImplementInInheritor((PsiClassType)expected, false);
          return ContainerUtil.findAll(pairs, pair -> methodName.equals(pair.first.getName()));
        }
      }
    }

    return Collections.emptyList();
  }

  @Nullable
  public static PsiType getSingleMethodParameterType(@Nullable PsiType type, int index, GrClosableBlock closure) {
    final PsiType[] signature = findSingleAbstractMethodSignature(type);
    if (signature != null && GrClosureSignatureUtil.isSignatureApplicable(GrClosureSignatureUtil.createSignature(closure), signature, closure)) {
      return signature.length > index ?  signature[index] : PsiType.NULL;
    }
    return null;
  }

  @Nullable
  public static PsiType[] findSingleAbstractMethodSignature(@Nullable PsiType type) {
    if (type instanceof PsiClassType && !(TypesUtil.isClassType(type, GroovyCommonClassNames.GROOVY_LANG_CLOSURE))) {
      List<Pair<PsiMethod, PsiSubstitutor>> result = getMethodsToOverrideImplementInInheritor((PsiClassType)type, true);
      if (result.size() == 1) {
        return getParameterTypes(result.get(0));
      }
    }
    return null;
  }

  public static PsiType[] getParameterTypes(final Pair<PsiMethod, PsiSubstitutor> pair) {
    return ContainerUtil.map2Array(pair.first.getParameterList().getParameters(), PsiType.class, psiParameter -> pair.second.substitute(psiParameter.getType()));
  }

  @NotNull
  public static List<Pair<PsiMethod, PsiSubstitutor>> getMethodsToOverrideImplementInInheritor(PsiClassType classType, boolean toImplement) {
    final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null) {
      return Collections.emptyList();
    }

    List<Pair<PsiMethod, PsiSubstitutor>> over = getMethodsToOverrideImplement(psiClass, false);
    List<Pair<PsiMethod, PsiSubstitutor>> impl = getMethodsToOverrideImplement(psiClass, true);
    
    for (PsiMethod method : psiClass.getMethods()) {
      (method.hasModifierProperty(PsiModifier.ABSTRACT) ? impl : over).add(Pair.create(method, PsiSubstitutor.EMPTY));
    }

    for (Iterator<Pair<PsiMethod, PsiSubstitutor>> iterator = impl.iterator(); iterator.hasNext();) {
      Pair<PsiMethod, PsiSubstitutor> pair = iterator.next();
      if (hasTraitImplementation(pair.first)) {
        iterator.remove();
        over.add(pair);
      }
    }

    final List<Pair<PsiMethod, PsiSubstitutor>> result = toImplement ? impl : over;
    for (int i = 0, resultSize = result.size(); i < resultSize; i++) {
      Pair<PsiMethod, PsiSubstitutor> pair = result.get(i);
      result.set(i, Pair.create(pair.first, resolveResult.getSubstitutor().putAll(pair.second)));
    }
    return result;
  }

  private static ArrayList<Pair<PsiMethod, PsiSubstitutor>> getMethodsToOverrideImplement(PsiClass psiClass, final boolean toImplement) {
    final ArrayList<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<>();
    for (CandidateInfo info : OverrideImplementExploreUtil.getMethodsToOverrideImplement(psiClass, toImplement)) {
      result.add(Pair.create((PsiMethod) info.getElement(), info.getSubstitutor()));
    }
    return result;
  }

  private static boolean hasTraitImplementation(PsiMethod method) {
    return method.getModifierList().findAnnotation("org.mbte.groovypp.runtime.HasDefaultImplementation") != null;
  }
}
