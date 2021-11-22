// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit2.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.siyeh.ig.junit.JUnitCommonClassNames.*;

public class JUnitImplicitUsageProvider implements ImplicitUsageProvider {
  private static final String MOCK = "org.mockito.Mock";
  private static final List<String> INJECTED_FIELD_ANNOTATIONS = Arrays.asList(
    MOCK,
    "org.mockito.Spy",
    "org.mockito.Captor",
    "org.mockito.InjectMocks",
    "org.assertj.core.api.junit.jupiter.InjectSoftAssertions");


  @Override
  public boolean isImplicitUsage(@NotNull PsiElement element) {
    return isReferencedInsideEnumSourceAnnotation(element) || isReferencedInsideMethodSourceAnnotation(element);
  }

  private static boolean isReferencedInsideMethodSourceAnnotation(@NotNull PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod) element;
    if (method.getAnnotation(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE) != null) return false;
    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return false;
    SearchScope useScope = psiClass.getUseScope();
    String methodName = method.getName();
    if (isExpensiveSearch(psiClass, methodName, useScope)) return false;
    Stream<PsiMethod> methodStream = Arrays.stream(psiClass.findMethodsByName(methodName, true))
      .filter(it -> it.getAnnotation(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST) != null
    && it.getAnnotation(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE) != null);
    return methodStream.findAny().isPresent();
  }
  private static boolean isReferencedInsideEnumSourceAnnotation(@NotNull PsiElement element) {
    if (element instanceof PsiEnumConstant) {
      PsiClass psiClass = ((PsiEnumConstant)element).getContainingClass();
      String className = psiClass != null ? psiClass.getName() : null;
      if (className == null) return false;
      SearchScope useScope = psiClass.getUseScope();

      if (isExpensiveSearch(psiClass, className, useScope)) return false;
      return ReferencesSearch.search(psiClass, useScope, false)
        .anyMatch(reference -> {
          PsiElement referenceElement = reference.getElement();
          PsiAnnotation annotation = PsiTreeUtil.getParentOfType(referenceElement, PsiAnnotation.class);
          if (annotation != null) {
            String annotationName = annotation.getQualifiedName();
            if (ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE.equals(annotationName) && annotation.getAttributes().size() == 1) {
              return true;
            }
          }
          return false;
        });
    }
    return false;
  }

  private static boolean isExpensiveSearch(PsiClass psiClass, String className, SearchScope useScope) {
    if (!(useScope instanceof LocalSearchScope)) {
      PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(psiClass.getProject());
      PsiSearchHelper.SearchCostResult cheapEnough = searchHelper.isCheapEnoughToSearch(className, (GlobalSearchScope)useScope, null, null);
      if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES ||
          cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isImplicitRead(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(@NotNull PsiElement element) {
    return element instanceof PsiParameter && AnnotationUtil.isAnnotated((PsiParameter)element, MOCK, 0) ||
           element instanceof PsiField && AnnotationUtil.isAnnotated((PsiField)element, INJECTED_FIELD_ANNOTATIONS, 0);
  }

  @Override
  public boolean isImplicitlyNotNullInitialized(@NotNull PsiElement element) {
    return isImplicitWrite(element);
  }
}
