// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GrVisibilityUtils")

package org.jetbrains.plugins.groovy.lang.resolve.ast

import com.intellij.psi.*
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil.inferStringAttribute
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.hasModifierProperty
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import groovy.transform.options.Visibility as GroovyVisibility

enum class Visibility {
  PRIVATE,
  PACKAGE_PRIVATE,
  PROTECTED,
  PUBLIC;

  @GrModifier.GrModifierConstant
  override fun toString(): String = when (this) {
    PRIVATE -> GrModifier.PRIVATE
    PACKAGE_PRIVATE -> GrModifier.PACKAGE_LOCAL
    PROTECTED -> GrModifier.PROTECTED
    PUBLIC -> GrModifier.PUBLIC
  }
}

fun extractVisibility(element : PsiModifierListOwner) : Visibility {
  val modifierList = element.modifierList as? GrModifierList ?: return Visibility.PUBLIC
  return when {
    hasModifierProperty(modifierList, "public", false) -> Visibility.PUBLIC
    hasModifierProperty(modifierList, "private", false) -> Visibility.PRIVATE
    hasModifierProperty(modifierList, "protected", false) -> Visibility.PROTECTED
    else -> Visibility.PACKAGE_PRIVATE
  }
}

/**
 * @param annotation: An annotation that has attribute `visibilityId`
 * @param sourceElement: An element for which visibility will be computed
 * @param defaultVisibility: [Visibility] that will be returned in case when no suitable visibility annotations found
 */
fun getVisibility(annotation: PsiAnnotation, sourceElement: PsiElement, defaultVisibility: Visibility): Visibility {
  val visAnnotations = PsiUtil.getAllAnnotations(sourceElement.navigationElement, GroovyCommonClassNames.GROOVY_TRANSFORM_VISIBILITY_OPTIONS)
  val visAnnotationId = inferStringAttribute(annotation, "visibilityId")
  val targetAnnotation = visAnnotations.firstOrNull { inferStringAttribute(it, "id") == visAnnotationId }
  if (targetAnnotation == null) return defaultVisibility

  val visibility: GroovyVisibility =
    when {
      sourceElement is PsiMethod && sourceElement.isConstructor -> inferGroovyVisibility(targetAnnotation, "constructor")
      sourceElement is PsiMethod -> inferGroovyVisibility(targetAnnotation, "method")
      sourceElement is PsiClass -> inferGroovyVisibility(targetAnnotation, "type")
      else -> null
    }?.takeUnless { it == GroovyVisibility.UNDEFINED }
     ?: inferGroovyVisibility(targetAnnotation, "value")
     ?.takeUnless { it == GroovyVisibility.UNDEFINED }
     ?: return defaultVisibility

  return when (visibility) {
    GroovyVisibility.UNDEFINED -> defaultVisibility
    GroovyVisibility.PUBLIC -> Visibility.PUBLIC
    GroovyVisibility.PROTECTED -> Visibility.PROTECTED
    GroovyVisibility.PACKAGE_PRIVATE -> Visibility.PACKAGE_PRIVATE
    GroovyVisibility.PRIVATE -> Visibility.PRIVATE
  }
}

private fun inferGroovyVisibility(annotation: PsiAnnotation, attributeName: String) : GroovyVisibility? {
  val targetValue = annotation.findAttributeValue(attributeName)
  return if (targetValue is PsiQualifiedReference) {
    try {
      targetValue.referenceName?.run(GroovyVisibility::valueOf) ?: return null
    }
    catch (e: IllegalArgumentException) {
      null
    }
  } else {
    null
  }
}

