// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;

import java.util.List;

public final class GrDefaultMethodComparator extends GrMethodComparator {

  @Override
  public Boolean dominated(@NotNull GroovyMethodResult result1,
                           @NotNull GroovyMethodResult result2,
                           @NotNull Context context) {
    final PsiMethod method1 = result1.getElement();
    final PsiSubstitutor substitutor1 = result1.getContextSubstitutor();
    final PsiElement resolveContext1 = result1.getCurrentFileResolveContext();

    final PsiMethod method2 = result2.getElement();
    final PsiSubstitutor substitutor2 = result2.getContextSubstitutor();
    final PsiElement resolveContext2 = result2.getCurrentFileResolveContext();

    final List<Argument> arguments = context.getArguments();

    if (context.isConstructor() && arguments != null && arguments.size() == 1) {
      if (method1.getParameterList().isEmpty()) return true;
      if (method2.getParameterList().isEmpty()) return false;
    }

    PsiParameter[] params1 = method1.getParameterList().getParameters();
    PsiParameter[] params2 = method2.getParameterList().getParameters();

    if (arguments != null && arguments.isEmpty()) {
      if (params2.length == 1 && params2[0].getType() instanceof PsiArrayType) return true;
    }

    if (arguments == null && params1.length != params2.length) return false;

    if (params1.length < params2.length) {
      PsiParameter last = ArrayUtil.getLastElement(params1);
      return last != null && last.getType() instanceof PsiArrayType;
    }
    else if (params1.length > params2.length) {
      PsiParameter last = ArrayUtil.getLastElement(params2);
      return !(last != null && last.getType() instanceof PsiArrayType);
    }

    final PsiElement myPlace = context.getPlace();
    for (int i = 0; i < params2.length; i++) {
      final PsiType pType1 = params1[i].getType();
      final PsiType pType2 = params2[i].getType();
      PsiType type1 = substitutor1.substitute(pType1);
      PsiType type2 = substitutor2.substitute(pType2);

      if (arguments != null && arguments.size() > i) {
        PsiType argType = arguments.get(i).getType();
        if (argType != null) {
          final boolean converts1 = TypesUtil.isAssignableWithoutConversions(TypeConversionUtil.erasure(type1), argType);
          final boolean converts2 = TypesUtil.isAssignableWithoutConversions(TypeConversionUtil.erasure(type2), argType);
          if (converts1 != converts2) {
            return converts2;
          }

          // see groovy.lang.GroovyCallable
          if (TypesUtil.resolvesTo(type1, CommonClassNames.JAVA_UTIL_CONCURRENT_CALLABLE) &&
              TypesUtil.resolvesTo(type2, CommonClassNames.JAVA_LANG_RUNNABLE)) {
            if (InheritanceUtil.isInheritor(argType, GroovyCommonClassNames.GROOVY_LANG_GROOVY_CALLABLE)) return true;
          }
        }
      }

      if (!typesAgree(TypeConversionUtil.erasure(pType1), TypeConversionUtil.erasure(pType2), context)) return false;

      if (resolveContext1 != null && resolveContext2 == null) {
        return !(TypesUtil.resolvesTo(type1, CommonClassNames.JAVA_LANG_OBJECT) &&
                 TypesUtil.resolvesTo(type2, CommonClassNames.JAVA_LANG_OBJECT));
      }

      if (resolveContext1 == null && resolveContext2 != null) {
        return true;
      }
    }

    if (!(method1 instanceof SyntheticElement) && !(method2 instanceof SyntheticElement)) {
      final PsiType returnType1 = substitutor1.substitute(method1.getReturnType());
      final PsiType returnType2 = substitutor2.substitute(method2.getReturnType());

      if (!TypesUtil.isAssignableWithoutConversions(returnType1, returnType2) &&
          TypesUtil.isAssignableWithoutConversions(returnType2, returnType1)) {
        return false;
      }
    }

    if (method1 instanceof GrGdkMethod && method2 instanceof GrGdkMethod) {
      PsiType firstReceiverType = ((GrGdkMethod)method1).getReceiverType();
      PsiType secondReceiverType = ((GrGdkMethod)method2).getReceiverType();
      if (!typesAgree(TypeConversionUtil.erasure(firstReceiverType), TypeConversionUtil.erasure(secondReceiverType), context)) return false;
    }

    return true;
  }

  private static boolean typesAgree(@NotNull PsiType type1, @NotNull PsiType type2, @NotNull Context context) {
    final boolean hasArguments = context.getArguments() != null;
    return hasArguments ? //resolve, otherwise same_name_variants
           TypesUtil.isAssignableWithoutConversions(type1, type2) :
           type1.equals(type2);
  }
}
