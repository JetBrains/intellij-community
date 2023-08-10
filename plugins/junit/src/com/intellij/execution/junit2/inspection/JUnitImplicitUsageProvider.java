// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST;
import static com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE;

public class JUnitImplicitUsageProvider implements ImplicitUsageProvider {
  private static final String MOCKITO_MOCK = "org.mockito.Mock";
  private static final String KOTLIN_JVM_STATIC = "kotlin.jvm.JvmStatic";

  private static final List<String> INJECTED_FIELD_ANNOTATIONS = Arrays.asList(
    MOCKITO_MOCK,
    "org.mockito.Spy",
    "org.mockito.Captor",
    "org.mockito.InjectMocks",
    "org.easymock.Mock",
    "org.assertj.core.api.junit.jupiter.InjectSoftAssertions", 
    "org.junit.jupiter.api.io.TempDir");


  @Override
  public boolean isImplicitUsage(@NotNull PsiElement element) {
    return element instanceof PsiParameter && isParameterUsedInParameterizedPresentation((PsiParameter)element) || isReferencedInsideEnumSourceAnnotation(element)
           || isReferencedInsideMethodSourceAnnotation(element);
  }

  private static boolean isParameterUsedInParameterizedPresentation(PsiParameter parameter) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod method) {
      PsiAnnotation annotation = method.getModifierList().findAnnotation(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST);
      if (annotation != null) {
        PsiAnnotationMemberValue attributeValue = annotation.findDeclaredAttributeValue("name");
        if (attributeValue instanceof PsiExpression) {
          String indexInDisplayName = "{" + method.getParameterList().getParameterIndex(parameter) + "}";
          Object value = JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)attributeValue, null, false);
          return indexInDisplayName.equals(value);
        }
      }
    }
    return false;
  }

  private static boolean isReferencedInsideEnumSourceAnnotation(@NotNull PsiElement element) {
    if (element instanceof PsiEnumConstant) {
      PsiClass psiClass = ((PsiEnumConstant)element).getContainingClass();
      return psiClass != null &&
             CachedValuesManager.getCachedValue(psiClass, 
                                                () -> CachedValueProvider.Result.create(isEnumClassReferencedInEnumSourceAnnotation(psiClass),
                                                                                        PsiModificationTracker.MODIFICATION_COUNT));
    }
    return false;
  }

  private static boolean isReferencedInsideMethodSourceAnnotation(@NotNull PsiElement element) {
    if (element instanceof PsiMethod psiMethod) {
      return CachedValuesManager.getCachedValue(psiMethod,
                                         () -> CachedValueProvider.Result.create(isReferencedInsideMethodSourceAnnotation(psiMethod),
                                                                                 PsiModificationTracker.MODIFICATION_COUNT));
    }
    return false;
  }

  private static boolean isReferencedInsideMethodSourceAnnotation(@NotNull PsiMethod psiMethod) {
    String methodName = psiMethod.getName();
    PsiClass psiClass = psiMethod.getContainingClass();
    if (psiMethod.getAnnotation(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE) != null) return false;
    if (psiMethod.getParameterList().getParametersCount() != 0) return false;
    if (!TestUtils.isInTestSourceContent(psiClass)) return false;

    if (psiMethod.hasAnnotation(KOTLIN_JVM_STATIC)) {
      PsiElement parent = psiClass.getParent();
      if (parent instanceof PsiClass) psiClass = (PsiClass)parent;
    }

    return ContainerUtil.exists(psiClass.findMethodsByName(methodName, false),
                                it -> psiMethod != it &&
                                      MetaAnnotationUtil.isMetaAnnotated(it, Collections.singleton(
                                        ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE)) &&
                                      MetaAnnotationUtil.isMetaAnnotated(it, Collections.singleton(
                                        ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST)));
  }

  private static boolean isEnumClassReferencedInEnumSourceAnnotation(PsiClass psiClass) {
    String className = psiClass.getName();
    if (className == null) {
      return false;
    }
    SearchScope useScope = psiClass.getUseScope();
    if (!shouldCheckClassUsages(psiClass, className, useScope)) {
      return false;
    }
    return ReferencesSearch.search(psiClass, useScope, false)
      .anyMatch(reference -> {
        PsiElement referenceElement = reference.getElement();
        PsiAnnotation annotation = PsiTreeUtil.getParentOfType(referenceElement, PsiAnnotation.class, true, PsiStatement.class, PsiMember.class);
        if (annotation != null) {
          String annotationName = annotation.getQualifiedName();
          if (JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ENUM_SOURCE.equals(annotationName) && annotation.getAttributes().size() == 1) {
            return true;
          }
        }
        return false;
      });
  }

  private static boolean shouldCheckClassUsages(PsiClass psiClass, String name, SearchScope useScope) {
    if (!(useScope instanceof LocalSearchScope)) {
      PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(psiClass.getProject());
      if (PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES ==
          searchHelper.isCheapEnoughToSearch(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE_SHORT, (GlobalSearchScope)useScope, null, null)) {
        return false;
      }
      PsiSearchHelper.SearchCostResult cheapEnough = searchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)useScope, null, null);
      if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES ||
          cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isImplicitRead(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public boolean isImplicitWrite(@NotNull PsiElement element) {
    return element instanceof PsiParameter && AnnotationUtil.isAnnotated((PsiParameter)element, MOCKITO_MOCK, 0) ||
           element instanceof PsiField && AnnotationUtil.isAnnotated((PsiField)element, INJECTED_FIELD_ANNOTATIONS, 0);
  }

  @Override
  public boolean isImplicitlyNotNullInitialized(@NotNull PsiElement element) {
    return isImplicitWrite(element);
  }
}
