package org.jetbrains.plugins.groovy.gpp;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.AbstractClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;

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
            if (type instanceof PsiClassType) {
              for (GroovyResolveResult resolveResult : GppTypeConverter
                .getConstructorCandidates((PsiClassType)type, ((GrTupleType)listType).getComponentTypes(), closure)) {
                final PsiElement method = resolveResult.getElement();
                if (method instanceof PsiMethod && ((PsiMethod)method).isConstructor()) {
                  final PsiType toCastTo =
                    resolveResult.getSubstitutor().substitute(((PsiMethod)method).getParameterList().getParameters()[argIndex].getType());
                  final PsiType suggestion = getSingleMethodParameterType(toCastTo, index, closure);
                  if (suggestion != null) {
                    return suggestion;
                  }
                }

              }
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
    final GrArgumentLabel label = namedArgument.getLabel();
    if (label == null) {
      return null;
    }

    final String methodName = label.getName();
    if (methodName == null) {
      return null;
    }

    final PsiElement map = namedArgument.getParent();
    if (map instanceof GrListOrMap && ((GrListOrMap)map).isMap()) {
      for (PsiType expected : GroovyExpectedTypesProvider.getDefaultExpectedTypes((GrExpression)map)) {
        if (expected instanceof PsiClassType) {
          final List<Pair<PsiMethod, PsiSubstitutor>> pairs = getMethodsToOverrideImplementInInheritor((PsiClassType)expected, false);
          final List<Pair<PsiMethod, PsiSubstitutor>> withName =
            ContainerUtil.findAll(pairs, new Condition<Pair<PsiMethod, PsiSubstitutor>>() {
              public boolean value(Pair<PsiMethod, PsiSubstitutor> pair) {
                return methodName.equals(pair.first.getName());
              }
            });
          if (withName.size() == 1) {
            return withName.get(0);
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static PsiType getSingleMethodParameterType(@Nullable PsiType type, int index, GrClosableBlock closure) {
    final PsiType[] signature = findSingleAbstractMethodSignature(type);
    if (signature != null && GrClosureSignatureUtil.isSignatureApplicable(GrClosureSignatureUtil.createSignature(closure), signature, closure)) {
      return signature.length > index ?  signature[index] : PsiType.NULL;
    }
    return null;
  }

  @Nullable
  public static PsiType[] findSingleAbstractMethodSignature(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      List<Pair<PsiMethod, PsiSubstitutor>> result = getMethodsToOverrideImplementInInheritor((PsiClassType)type, true);
      if (result.size() == 1) {
        final Pair<PsiMethod, PsiSubstitutor> pair = result.get(0);
        return ContainerUtil.map2Array(pair.first.getParameterList().getParameters(), PsiType.class, new Function<PsiParameter, PsiType>() {
          public PsiType fun(PsiParameter psiParameter) {
            return pair.second.substitute(psiParameter.getType());
          }
        });
      }
    }
    return null;
  }

  @NotNull
  private static List<Pair<PsiMethod, PsiSubstitutor>> getMethodsToOverrideImplementInInheritor(PsiClassType classType, boolean toImplement) {
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
    final ArrayList<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
    for (CandidateInfo info : OverrideImplementUtil.getMethodsToOverrideImplement(psiClass, toImplement)) {
      result.add(Pair.create((PsiMethod) info.getElement(), info.getSubstitutor()));
    }
    return result;
  }

  private static boolean hasTraitImplementation(PsiMethod method) {
    return method.getModifierList().findAnnotation("org.mbte.groovypp.runtime.HasDefaultImplementation") != null;
  }
}
