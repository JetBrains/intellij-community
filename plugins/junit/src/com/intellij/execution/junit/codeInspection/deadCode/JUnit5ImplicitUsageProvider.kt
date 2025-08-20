// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection.deadCode

import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.psi.*
import com.intellij.psi.impl.JavaConstantExpressionEvaluator
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.siyeh.ig.junit.JUnitCommonClassNames.*
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElement

private fun parameterIsUsedByParameterizedTest(parameter: PsiParameter): Boolean {
  val declarationScope = parameter.declarationScope
  if (declarationScope !is PsiMethod) return false
  val annotation = declarationScope.modifierList.findAnnotation(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST)
  if (annotation != null) {
    val attributeValue = annotation.findDeclaredAttributeValue("name")
    if (attributeValue is PsiExpression) {
      val indexInDisplayName = "{" + declarationScope.parameterList.getParameterIndex(parameter) + "}"
      val value = JavaConstantExpressionEvaluator.computeConstantExpression(attributeValue as PsiExpression?, null, false)
      return indexInDisplayName == value
    }
  }
  return false
}

private fun enumReferenceIsUsedByParameterizedTest(element: PsiEnumConstant): Boolean {
  fun isCheapEnough(psiClass: PsiClass, name: String, useScope: SearchScope): Boolean {
    if (useScope is LocalSearchScope) return true
    val searchHelper = PsiSearchHelper.getInstance(psiClass.project)
    if (SearchCostResult.ZERO_OCCURRENCES == searchHelper.isCheapEnoughToSearch(ORG_JUNIT_JUPITER_PARAMS_ENUM_SOURCE_SHORT, (useScope as GlobalSearchScope), null)) {
      return false
    }
    val cheapEnough = searchHelper.isCheapEnoughToSearch(name, useScope, null)
    return !(cheapEnough == SearchCostResult.ZERO_OCCURRENCES || cheapEnough == SearchCostResult.TOO_MANY_OCCURRENCES)
  }

  fun check(psiClass: PsiClass): Boolean {
    val className = psiClass.name ?: return false
    val useScope = psiClass.useScope
    if (!isCheapEnough(psiClass, className, useScope)) return false
    return ReferencesSearch.search(psiClass, useScope, false).anyMatch { reference ->
      val referenceElement = reference.element.toUElement() ?: return@anyMatch false
      val annotation = referenceElement.getParentOfType<UAnnotation>() ?: return@anyMatch false
      annotation.qualifiedName == ORG_JUNIT_JUPITER_PARAMS_PROVIDER_ENUM_SOURCE && annotation.attributeValues.size == 1
    }
  }

  val containingClass = element.containingClass
  return containingClass != null && CachedValuesManager.getCachedValue(containingClass) {
    CachedValueProvider.Result.create(check(containingClass), PsiModificationTracker.MODIFICATION_COUNT)
  }
}

private fun methodSourceIsImplicitlyUsed(element: PsiMethod): Boolean {
  fun check(psiMethod: PsiMethod): Boolean {
    val methodName = psiMethod.name
    var psiClass = psiMethod.containingClass ?: return false
    if (psiMethod.getAnnotation(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE) != null) return false
    if (psiMethod.parameterList.parametersCount != 0) return false

    if (psiMethod.hasAnnotation("kotlin.jvm.JvmStatic")) {
      val parent = psiClass.parent
      if (parent is PsiClass) psiClass = parent
    }

    return psiClass.methods.any { otherMethod ->
      MetaAnnotationUtil.isMetaAnnotated(otherMethod, setOf(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE))
      && MetaAnnotationUtil.isMetaAnnotated(otherMethod, setOf(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST))
      && isAnnotationMemberContainsName(methodName, otherMethod, ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE)
    }
  }
  return CachedValuesManager.getCachedValue(element) {
    CachedValueProvider.Result.create(check(element), PsiModificationTracker.MODIFICATION_COUNT)
  }
}

private fun fieldSourceIsImplicitlyUsed(element: PsiField): Boolean {
  fun check(psiField: PsiField): Boolean {
    val fieldName = psiField.name
    var psiClass = psiField.containingClass ?: return false

    if (psiField.hasAnnotation("kotlin.jvm.JvmStatic")) {
      val parent = psiClass.parent
      if (parent is PsiClass) psiClass = parent
    }

    return psiClass.methods.any { method ->
      MetaAnnotationUtil.isMetaAnnotated(method, setOf(ORG_JUNIT_JUPITER_PARAMS_PROVIDER_FIELD_SOURCE))
      && MetaAnnotationUtil.isMetaAnnotated(method, setOf(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_TEST))
      && isAnnotationMemberContainsName(fieldName, method, ORG_JUNIT_JUPITER_PARAMS_PROVIDER_FIELD_SOURCE)
    }
  }

  return CachedValuesManager.getCachedValue(element) {
    CachedValueProvider.Result.create(check(element), PsiModificationTracker.MODIFICATION_COUNT)
  }
}

private fun isAnnotationMemberContainsName(name: String, method: PsiMethod, annotationFqn: String): Boolean {
  val annotation = method.getAnnotation(annotationFqn) ?: return false
  val value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)
  if (value == null && method.name == name) return true
  if (value is PsiArrayInitializerMemberValue) {
    if (value.initializers.isEmpty() && method.name == name) return true
    for (memberValue in value.initializers) {
      if (memberValue is PsiLiteralExpression) {
        val data = JavaConstantExpressionEvaluator.computeConstantExpression(memberValue, null, false) as? String ?: continue
        if (data.isEmpty() && method.name == name) return true
        if (data == name) return true
      }
    }
  }
  return false
}

class JUnit5ImplicitUsageProvider : ImplicitUsageProvider {
  override fun isImplicitUsage(element: PsiElement): Boolean {
    return (element is PsiParameter && parameterIsUsedByParameterizedTest(element))
           || (element is PsiEnumConstant && enumReferenceIsUsedByParameterizedTest(element))
           || (element is PsiMethod && methodSourceIsImplicitlyUsed(element))
           || (element is PsiField && fieldSourceIsImplicitlyUsed(element))
  }

  override fun isImplicitRead(element: PsiElement): Boolean = false

  override fun isImplicitWrite(element: PsiElement): Boolean {
    return element is PsiField && MetaAnnotationUtil.isMetaAnnotated(element, setOf(ORG_JUNIT_JUPITER_API_IO_TEMPDIR))
  }

  override fun isImplicitlyNotNullInitialized(element: PsiElement): Boolean = isImplicitWrite(element)
}