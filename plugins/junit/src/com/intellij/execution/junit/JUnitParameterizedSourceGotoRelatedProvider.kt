// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInspection.flattenedAttributeValues
import com.intellij.execution.junit.references.MethodSourceReference
import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

class JUnitParameterizedSourceGotoRelatedProvider : GotoRelatedProvider() {
  override fun getItems(psiElement: PsiElement): List<GotoRelatedItem> {
    val uElement = psiElement.parent.toUElementOfType<UMethod>() ?: return emptyList()
    val javaMethod = uElement.javaPsi
    val annotations = MetaAnnotationUtil.findMetaAnnotations(
      javaMethod,
      setOf(JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PROVIDER_METHOD_SOURCE)
    ).toList()
    if (annotations.isEmpty()) return emptyList()
    return annotations.flatMap { annotation ->
      methodSourceItems(javaMethod, annotation)
    }
  }

  private fun methodSourceItems(method: PsiMethod, annotation: PsiAnnotation): List<GotoRelatedItem> {
    val methods = findMethodSources(method, annotation)
    return methods.map { GotoRelatedItem(it) }
  }

  private fun findMethodSources(method: PsiMethod, annotation: PsiAnnotation): List<PsiMethod> {
    val annotationMemberValue = annotation.flattenedAttributeValues("value")
    return if (annotationMemberValue.isEmpty()) {
      val containingClass = method.containingClass ?: return emptyList()
      if (annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME) == null) return emptyList()
      val sourceMethod = containingClass.findMethodsByName(method.name, true).singleOrNull {
        it.parameters.isEmpty()
      } ?: return emptyList()
      listOf(sourceMethod)
    } else {
      annotationMemberValue.flatMap { annotationValue ->
        annotationValue.references.mapNotNull { ref ->
          if (ref !is MethodSourceReference) return@mapNotNull null
          ref.resolve() as? PsiMethod
        }
      }
    }
  }
}