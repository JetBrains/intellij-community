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

public class GrDefaultMethodComparator extends GrMethodComparator {

  @Override
  public Boolean dominated(@NotNull GroovyMethodResult result1,
                           @NotNull GroovyMethodResult result2,
                           @NotNull Context context) {
    PsiMethod method1 = result1.getElement();
    final PsiSubstitutor substitutor1 = result1.getSubstitutor(false);
    final PsiElement resolveContext1 = result1.getCurrentFileResolveContext();
    PsiMethod method2 = result2.getElement();
    final PsiSubstitutor substitutor2 = result2.getSubstitutor(false);
    final PsiElement resolveContext2 = result2.getCurrentFileResolveContext();

    final PsiType[] argTypes;
    if (method1 instanceof GrGdkMethod && method2 instanceof GrGdkMethod) {
      method1 = ((GrGdkMethod)method1).getStaticMethod();
      method2 = ((GrGdkMethod)method2).getStaticMethod();
      final PsiType[] contextArgumentTypes = context.getArgumentTypes();
      if (contextArgumentTypes == null) {
        argTypes = null;
      }
      else {
        argTypes = PsiType.createArray(contextArgumentTypes.length + 1);
        System.arraycopy(contextArgumentTypes, 0, argTypes, 1, contextArgumentTypes.length);
        argTypes[0] = context.getThisType();
      }
    }
    else if (method1 instanceof GrGdkMethod) {
      return true;
    }
    else if (method2 instanceof GrGdkMethod) {
      return false;
    }
    else {
      argTypes = context.getArgumentTypes();
    }

    if (context.isConstructor() && argTypes != null && argTypes.length == 1) {
      if (method1.getParameterList().getParametersCount() == 0) return true;
      if (method2.getParameterList().getParametersCount() == 0) return false;
    }

    PsiParameter[] params1 = method1.getParameterList().getParameters();
    PsiParameter[] params2 = method2.getParameterList().getParameters();

    if (argTypes != null && argTypes.length == 0) {
      if (params2.length == 1 && params2[0].getType() instanceof PsiArrayType) return true;
    }

    if (argTypes == null && params1.length != params2.length) return false;

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

      if (argTypes != null && argTypes.length > i) {
        PsiType argType = argTypes[i];
        if (argType != null) {
          final boolean converts1 = TypesUtil.isAssignableWithoutConversions(TypeConversionUtil.erasure(type1), argType, myPlace);
          final boolean converts2 = TypesUtil.isAssignableWithoutConversions(TypeConversionUtil.erasure(type2), argType, myPlace);
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

      if (!TypesUtil.isAssignableWithoutConversions(returnType1, returnType2, myPlace) &&
          TypesUtil.isAssignableWithoutConversions(returnType2, returnType1, myPlace)) {
        return false;
      }
    }

    return true;
  }

  private static boolean typesAgree(@NotNull PsiType type1, @NotNull PsiType type2, @NotNull Context context) {
    final boolean hasArguments = context.getArgumentTypes() != null;
    if (hasArguments && type1 instanceof PsiArrayType && !(type2 instanceof PsiArrayType)) {
      type1 = ((PsiArrayType)type1).getComponentType();
    }
    return hasArguments ? //resolve, otherwise same_name_variants
           TypesUtil.isAssignableWithoutConversions(type1, type2, context.getPlace()) :
           type1.equals(type2);
  }
}
