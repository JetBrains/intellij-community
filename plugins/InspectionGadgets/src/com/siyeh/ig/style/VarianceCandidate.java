// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** void doProcess(Processor<T> processor) */
class VarianceCandidate {
  final PsiMethod method; // doProcess
  final PsiParameter methodParameter; // processor
  final int methodParameterIndex; // 0
  final PsiTypeParameter typeParameter; // Processor.T
  final PsiType type; // T
  final int typeParameterIndex; // 0 - index in "Processor".getTypeParameters()
  final List<PsiMethod> superMethods = new ArrayList<>();

  private VarianceCandidate(@NotNull PsiParameter methodParameter,
                            @NotNull PsiMethod method,
                            int methodParameterIndex,
                            @NotNull PsiTypeParameter typeParameter,
                            @NotNull PsiType type,
                            int typeParameterIndex) {
    this.methodParameter = methodParameter;
    this.method = method;
    this.methodParameterIndex = methodParameterIndex;
    this.typeParameter = typeParameter;
    this.type = type;
    this.typeParameterIndex = typeParameterIndex;
  }

  static VarianceCandidate findVarianceCandidate(@NotNull PsiTypeElement innerTypeElement) {
    PsiType type = innerTypeElement.getType();
    if (type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType) return null;
    PsiElement parent = innerTypeElement.getParent();
    if (!(parent instanceof PsiReferenceParameterList)) return null;
    PsiElement pp = parent.getParent();
    if (!(pp instanceof PsiJavaCodeReferenceElement)) return null;
    PsiJavaCodeReferenceElement refElement = (PsiJavaCodeReferenceElement)pp;
    if (!parent.equals(refElement.getParameterList())) return null;
    JavaResolveResult result = refElement.advancedResolve(false);
    if (!(result.getElement() instanceof PsiClass)) return null;
    PsiClass resolved = (PsiClass)result.getElement();
    int index = ArrayUtil.indexOf(((PsiReferenceParameterList)parent).getTypeParameterElements(), innerTypeElement);

    PsiElement p3 = pp.getParent();
    if (!(p3 instanceof PsiTypeElement)) return null;
    PsiElement p4 = p3.getParent();
    if (!(p4 instanceof PsiParameter)) return null;
    PsiParameter parameter = (PsiParameter)p4;
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) return null;

    PsiTypeParameter[] typeParameters = resolved.getTypeParameters();
    if (typeParameters.length <= index) return null;
    PsiTypeParameter typeParameter = typeParameters[index];

    PsiMethod method = (PsiMethod)scope;
    PsiParameterList parameterList = method.getParameterList();
    int parameterIndex = parameterList.getParameterIndex(parameter);
    if (parameterIndex == -1) return null;
    PsiParameter[] methodParameters = parameterList.getParameters();
    VarianceCandidate candidate = new VarianceCandidate(parameter, method, parameterIndex, typeParameter, type, index);

    // check that if there is a super method, then it's parameterized similarly.
    // otherwise, it would make no sense to wildcardize "new Function<List<T>, T>(){ T apply(List<T> param) {...} }"
    // Oh, and make sure super methods are all modifiable, or it wouldn't make sense to report them
    if (!
    SuperMethodsSearch.search(method, null, true, true).forEach((MethodSignatureBackedByPsiMethod superMethod)-> {
      ProgressManager.checkCanceled();
      if (superMethod.getMethod() instanceof PsiCompiledElement) return false;
      // check not substituted super parameters
      PsiParameter[] superMethodParameters = superMethod.getMethod().getParameterList().getParameters();
      if (superMethodParameters.length != methodParameters.length) return false;
      PsiType superParameterType = superMethodParameters[parameterIndex].getType();
      if (!(superParameterType instanceof PsiClassType)) return false;
      PsiType[] superTypeParameters = ((PsiClassType)superParameterType).getParameters();
      candidate.superMethods.add(superMethod.getMethod());
      return superTypeParameters.length == typeParameters.length;
    })) return null;
    return candidate;
  }

  VarianceCandidate getSuperMethodVarianceCandidate(@NotNull PsiMethod superMethod) {
    PsiParameter superMethodParameter = superMethod.getParameterList().getParameters()[this.methodParameterIndex];
    PsiClass paraClass = ((PsiClassType)superMethodParameter.getType()).resolve();
    PsiTypeParameter superTypeParameter = paraClass.getTypeParameters()[this.typeParameterIndex];
    PsiTypeElement superMethodParameterTypeElement = superMethodParameter.getTypeElement();
    if (superMethodParameterTypeElement == null) return null; // e.g. when java overrides kotlin
    PsiJavaCodeReferenceElement ref = superMethodParameterTypeElement.getInnermostComponentReferenceElement();
    PsiTypeElement[] typeElements = ref.getParameterList().getTypeParameterElements();
    PsiType type = typeElements[this.typeParameterIndex].getType();

    return new VarianceCandidate(superMethodParameter, superMethod, this.methodParameterIndex, superTypeParameter, type, typeParameterIndex);
  }
}
