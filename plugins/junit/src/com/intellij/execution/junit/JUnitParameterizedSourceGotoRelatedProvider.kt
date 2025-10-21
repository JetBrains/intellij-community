// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInspection.flattenedAttributeValues
import com.intellij.execution.junit.references.BaseJunitAnnotationReference
import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.psi.*
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElementOfType

abstract class JUnitParameterizedSourceGotoRelatedProvider<Psi : PsiMember> : GotoRelatedProvider() {
  protected abstract val annotationClassName: String
  protected abstract fun getPsiElementByName(directClass: PsiClass, name: String): Psi?

  override fun getItems(psiElement: PsiElement): List<GotoRelatedItem> {
    val uElement = psiElement.parent.toUElementOfType<UMethod>() ?: return emptyList()
    val javaMethod = uElement.javaPsi
    val annotations = MetaAnnotationUtil.findMetaAnnotations(
      javaMethod,
      setOf(annotationClassName)
    ).toList()
    if (annotations.isEmpty()) return emptyList()
    return annotations.flatMap { annotation ->
      elementSourceItems(javaMethod, annotation)
    }
  }

  private fun elementSourceItems(method: PsiMethod, annotation: PsiAnnotation): List<GotoRelatedItem> {
    val methods = findElementSources(method, annotation)
    return methods.map { GotoRelatedItem(it) }
  }

  private fun findElementSources(method: PsiMethod, annotation: PsiAnnotation): List<PsiElement> {
    val annotationMemberValue = annotation.flattenedAttributeValues("value")
    return (if (annotationMemberValue.isEmpty()) {
      val containingClass = method.containingClass ?: return emptyList()
      if (annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME) == null) return emptyList()
      val element = getPsiElementByName(containingClass, method.name) ?: return emptyList()
      listOf(element)
    } else {
      annotationMemberValue.flatMap { annotationValue ->
        annotationValue.references.mapNotNull { ref ->
          if (ref !is BaseJunitAnnotationReference<*, *>) return@mapNotNull null
          ref.resolve()
        }
      }
    })
  }
}