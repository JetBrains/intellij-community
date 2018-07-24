// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

class VarianceUtil {
  @NotNull
  private static Variance getMethodSignatureVariance(@NotNull PsiMethod method, @NotNull PsiTypeParameter typeParameter) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    PsiClass methodClass = method.getContainingClass();
    if (methodClass == null || !(owner instanceof PsiClass)) return Variance.INVARIANT;
    PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getMaybeSuperClassSubstitutor(methodClass, (PsiClass)owner, PsiSubstitutor.EMPTY);
    if (superClassSubstitutor == null) return Variance.INVARIANT;

    Variance r = Variance.NOVARIANT;
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      PsiType type = parameter.getType();
      if (typeResolvesTo(type, typeParameter, superClassSubstitutor)) {
        r = Variance.CONTRAVARIANT;
      }
      else if (containsDeepIn(type, typeParameter, superClassSubstitutor, false)) {
        return Variance.INVARIANT;
      }
    }
    PsiType returnType = method.getReturnType();

    if (returnType != null && !TypeConversionUtil.isPrimitiveAndNotNullOrWrapper(returnType)) {
      if (typeResolvesTo(returnType, typeParameter, superClassSubstitutor)) {
        r = r.combine(Variance.COVARIANT);
      }
      else if (isComposeMethod(method, returnType, typeParameter, superClassSubstitutor)) {
        // ignore
      }
      else if (containsDeepIn(returnType, typeParameter, superClassSubstitutor, false)) {
        r = Variance.INVARIANT;
      }
    }
    return r;
  }

  // java.util.Function contains "<V> Function<T, V> andThen(Function<? super R, ? extends V> after)" which doesn't preclude it to be contravariant on T
  private static boolean isComposeMethod(@NotNull PsiMethod method,
                                         @NotNull PsiType returnType,
                                         @NotNull PsiTypeParameter typeParameter,
                                         @NotNull PsiSubstitutor superClassSubstitutor) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !(returnType instanceof PsiClassType) || !containingClass.equals(((PsiClassType)returnType).resolve())) {
      return false;
    }
    PsiTypeParameterListOwner typeParameterOwner = typeParameter.getOwner();
    PsiTypeParameterList typeParameterList = typeParameterOwner == null ? null : typeParameterOwner.getTypeParameterList();
    int index = typeParameterList == null ? -1 : typeParameterList.getTypeParameterIndex(typeParameter);

    PsiType[] parameters = ((PsiClassType)returnType).getParameters();
    if (index == -1 || parameters.length <= index) return false;
    return typeResolvesTo(parameters[index], typeParameter, superClassSubstitutor);
  }

  static boolean containsDeepIn(@NotNull PsiType rootType,
                                @NotNull PsiTypeParameter parameter,
                                @NotNull PsiSubstitutor superClassSubstitutor, boolean ignoreWildcardT) {
    return rootType.accept(new PsiTypeVisitor<Boolean>() {
      @NotNull
      @Override
      public Boolean visitClassType(PsiClassType classType) {
        for (PsiType param : classType.getParameters()) {
          if (param.accept(this)) return true;
        }
        return visitType(classType);
      }

      @NotNull
      @Override
      public Boolean visitArrayType(PsiArrayType arrayType) {
        return arrayType.getComponentType().accept(this);
      }

      @NotNull
      @Override
      public Boolean visitWildcardType(PsiWildcardType wildcardType) {
        PsiType bound = wildcardType.getBound();
        if (bound == null) {
          return false;
        }
        if (ignoreWildcardT && typeResolvesTo(bound, parameter, superClassSubstitutor)) return false;
        return bound.accept(this);
      }

      @NotNull
      @Override
      public Boolean visitType(PsiType type) {
        return typeResolvesTo(type, parameter, superClassSubstitutor);
      }
    });
  }

  static boolean typeResolvesTo(@NotNull PsiType type,
                                @NotNull PsiTypeParameter typeParameter,
                                @NotNull PsiSubstitutor superClassSubstitutor) {
    PsiType substituted = superClassSubstitutor.substitute(type);
    if (!(substituted instanceof PsiClassType))  return false;
    PsiClassType.ClassResolveResult result = ((PsiClassType)substituted).resolveGenerics();
    return typeParameter.equals(result.getElement()) && result.getSubstitutor().equals(PsiSubstitutor.EMPTY);
  }

  @NotNull
  static Variance getClassVariance(@NotNull PsiClass aClass, @NotNull PsiTypeParameter typeParameter) {
    Variance result = Variance.NOVARIANT;
    for (PsiMethod method : aClass.getAllMethods()) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null ||
          CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName()) ||
          method.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      result = result.combine(getMethodSignatureVariance(method, typeParameter));
      if (result == Variance.INVARIANT) break;
    }
    return result;
  }

  // doesn't make sense to replace "<T> boolean lone(Processor<T> p)" with "<T> boolean lone(Processor<? super T> p)" because
  // all method calls using the latter signature are compatible with the former method signature.
  // return true if the method declaration is impervious to wildcardization of type parameter "T" to "? extends T" or "? super T"
  // algorithm:
  // 1) ignore all parameters which don't contain T and bounded wildcards on T (they don't affect the further wildcardization)
  // 2) wildcardization of "X<T>" to "X<? super T>" is useless iff the method return type doesn't contain "T' and
  //   (the "X<T>" is the only parameter or all other parameters are of type T)
  // 3) wildcardization of "X<T>" to "X<? extends T>" is useless iff the method returns ("T" or something which doesn't contain "T") and
  //   (the "X<T>" is the only parameter)
  static boolean wildCardIsUseless(@NotNull VarianceCandidate candidate, boolean isExtends) {
    if (!(candidate.type instanceof PsiClassType)) return false;
    PsiClassType type = (PsiClassType)candidate.type;
    PsiClassType.ClassResolveResult resolve = type.resolveGenerics();
    PsiClass typeParameter = resolve.getElement();
    if (!(typeParameter instanceof PsiTypeParameter)) return false;
    PsiManager psiManager = typeParameter.getManager();
    // consider only type-parameterized methods: "<T> method(X<T>...)"
    if (!psiManager.areElementsEquivalent(((PsiTypeParameter)typeParameter).getOwner(), candidate.method)) return false;

    PsiType returnType = candidate.method.getReturnType();
    if (returnType == null) return true;
    boolean returnContainsT = containsDeepIn(returnType, (PsiTypeParameter)typeParameter, resolve.getSubstitutor(), false);
    if (!isExtends && returnContainsT) return false;

    PsiParameter[] parameters = candidate.method.getParameterList().getParameters();
    for (PsiParameter parameter : parameters) {
      if (psiManager.areElementsEquivalent(candidate.methodParameter, parameter)) continue;
      PsiType parameterType = parameter.getType();
      if (!containsDeepIn(parameterType, (PsiTypeParameter)typeParameter, resolve.getSubstitutor(), true)) continue;
      if (!isExtends && !typeResolvesTo(parameterType, (PsiTypeParameter)typeParameter, resolve.getSubstitutor())) return false;
      if (isExtends) return false;
    }

    return !isExtends
           || typeResolvesTo(returnType, (PsiTypeParameter)typeParameter, resolve.getSubstitutor())
           || !returnContainsT;
  }

  static boolean areBoundsSaturated(@NotNull VarianceCandidate candidate, boolean isExtends) {
    if (!(candidate.type instanceof PsiClassType)) return true;
    PsiClass aClass = ((PsiClassType)candidate.type).resolve();
    if (aClass == null) return true;
    if (isExtends) {
      return aClass.hasModifierProperty(PsiModifier.FINAL) || CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName());
    }
    return TypeUtils.isJavaLangObject(candidate.type);
  }
}
