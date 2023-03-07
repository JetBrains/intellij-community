// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/* void doProcess(Processor<T> processor) */
final class VarianceCandidate {
  final PsiMethod method; // doProcess
  final PsiParameter methodParameter; // processor
  final PsiClassReferenceType methodParameterType; // Processor<T>
  final int methodParameterIndex; // 0
  final PsiTypeParameter typeParameter; // Processor.T
  final PsiType type; // T
  final int typeParameterIndex; // 0 - index in "Processor".getTypeParameters()
  final List<PsiMethod> superMethods = new ArrayList<>();

  private VarianceCandidate(@NotNull PsiParameter methodParameter,
                            @NotNull PsiClassReferenceType methodParameterType,
                            @NotNull PsiMethod method,
                            int methodParameterIndex,
                            @NotNull PsiTypeParameter typeParameter,
                            @NotNull PsiType type,
                            int typeParameterIndex) {
    this.methodParameter = methodParameter;
    this.methodParameterType = methodParameterType;
    this.method = method;
    this.methodParameterIndex = methodParameterIndex;
    this.typeParameter = typeParameter;
    this.type = type;
    this.typeParameterIndex = typeParameterIndex;
  }

  static VarianceCandidate findVarianceCandidate(@NotNull PsiTypeElement innerTypeElement) {
    PsiType type = innerTypeElement.getType();
    if (type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType) return null;
    if (!(innerTypeElement.getParent() instanceof PsiReferenceParameterList referenceParameterList)) return null;
    if (!(referenceParameterList.getParent() instanceof PsiJavaCodeReferenceElement refElement)) return null;
    if (!referenceParameterList.equals(refElement.getParameterList())) return null;
    if (!(refElement.advancedResolve(false).getElement() instanceof PsiClass resolved)) return null;
    int index = ArrayUtil.indexOf(referenceParameterList.getTypeParameterElements(), innerTypeElement);

    if (!(refElement.getParent() instanceof PsiTypeElement typeElement)) return null;
    if (!(typeElement.getParent() instanceof PsiParameter parameter)) return null;
    if (!(parameter.getDeclarationScope() instanceof PsiMethod method)) return null;

    PsiTypeParameter[] typeParameters = resolved.getTypeParameters();
    if (typeParameters.length <= index) return null;
    PsiTypeParameter typeParameter = typeParameters[index];

    PsiParameterList parameterList = method.getParameterList();
    int parameterIndex = parameterList.getParameterIndex(parameter);
    if (parameterIndex == -1) return null;
    if (!(parameter.getType() instanceof PsiClassReferenceType classReferenceType)) return null;
    PsiParameter[] methodParameters = parameterList.getParameters();
    VarianceCandidate candidate = new VarianceCandidate(parameter, classReferenceType, method, parameterIndex, typeParameter, type, index);

    // check that if there is a super method, then it's parameterized similarly.
    // otherwise, it would make no sense to wildcardize "new Function<List<T>, T>(){ T apply(List<T> param) {...} }"
    // Oh, and make sure super methods are all modifiable, or it wouldn't make sense to report them
    if (!SuperMethodsSearch.search(method, null, true, true).forEach((MethodSignatureBackedByPsiMethod superMethod)-> {
      ProgressManager.checkCanceled();
      if (superMethod.getMethod() instanceof PsiCompiledElement) return false;
      // check not substituted super parameters
      PsiParameter[] superMethodParameters = superMethod.getMethod().getParameterList().getParameters();
      if (superMethodParameters.length != methodParameters.length) return false;
      PsiType superParameterType = superMethodParameters[parameterIndex].getType();
      if (!(superParameterType instanceof PsiClassType classType)) return false;
      PsiType[] superTypeParameters = classType.getParameters();
      candidate.superMethods.add(superMethod.getMethod());
      return superTypeParameters.length == typeParameters.length;
    })) return null;
    return candidate;
  }

  VarianceCandidate getSuperMethodVarianceCandidate(@NotNull PsiMethod superMethod) {
    PsiParameter superMethodParameter = superMethod.getParameterList().getParameters()[this.methodParameterIndex];
    PsiType superMethodParameterType = superMethodParameter.getType();
    PsiClass paraClass = ((PsiClassType)superMethodParameterType).resolve();
    PsiTypeParameter superTypeParameter = paraClass.getTypeParameters()[typeParameterIndex];
    PsiTypeElement superMethodParameterTypeElement = superMethodParameter.getTypeElement();
    if (superMethodParameterTypeElement == null) return null; // e.g. when java overrides kotlin
    PsiJavaCodeReferenceElement ref = superMethodParameterTypeElement.getInnermostComponentReferenceElement();
    PsiTypeElement[] typeElements = ref.getParameterList().getTypeParameterElements();
    PsiType type = typeElements[this.typeParameterIndex].getType();

    return new VarianceCandidate(superMethodParameter, (PsiClassReferenceType)superMethodParameterType, superMethod, this.methodParameterIndex, superTypeParameter, type, typeParameterIndex);
  }
}
