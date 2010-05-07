package org.jetbrains.plugins.groovy.gpp;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.AbstractClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureSignatureUtil;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author peter
 */
public class GppClosureParameterTypeProvider extends AbstractClosureParameterEnhancer {
  @Override
  protected PsiType getClosureParameterType(GrClosableBlock closure, int index) {
    if (!GppTypeConverter.hasTypedContext(closure)) {
      return null;
    }

    final PsiElement parent = closure.getParent();
    if (parent instanceof GrVariable) {
      return getSingleMethodParameterType(((GrVariable)parent).getDeclaredType(), index, closure);
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
      PsiClassType classType = (PsiClassType)type;
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass psiClass = resolveResult.getElement();
      if (psiClass == null) {
        return null;
      }

      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final Collection<MethodSignature> signatures = OverrideImplementUtil.getMethodSignaturesToImplement(psiClass);
      for (Iterator<MethodSignature> iterator = signatures.iterator(); iterator.hasNext();) {
        MethodSignature next = iterator.next();
        if (next instanceof MethodSignatureBackedByPsiMethod) {
          final PsiMethod method = ((MethodSignatureBackedByPsiMethod)next).getMethod();
          if (hasTraitImplementation(method)) {
            iterator.remove();
          }
        }
      }

      if (signatures.size() == 1) {
        final PsiType[] parameterTypes = signatures.iterator().next().getParameterTypes();
        return ContainerUtil.map2Array(parameterTypes, PsiType.class, new Function<PsiType, PsiType>() {
          public PsiType fun(PsiType type) {
            return substitutor.substitute(type);
          }
        });
      }
      else if (signatures.isEmpty()) {
        final List<PsiMethod> abstractMethods = ContainerUtil.findAll(psiClass.getMethods(), new Condition<PsiMethod>() {
          public boolean value(PsiMethod method) {
            return method.hasModifierProperty(PsiModifier.ABSTRACT) && !hasTraitImplementation(method);
          }
        });
        if (abstractMethods.size() == 1) {
          return ContainerUtil.map2Array(abstractMethods.get(0).getParameterList().getParameters(), PsiType.class, new Function<PsiParameter, PsiType>() {
            public PsiType fun(PsiParameter psiParameter) {
              return substitutor.substitute(psiParameter.getType());
            }
          });
        }
      }
    }
    return null;
  }

  private static boolean hasTraitImplementation(PsiMethod method) {
    return method.getModifierList().findAnnotation("org.mbte.groovypp.runtime.HasDefaultImplementation") != null;
  }
}
