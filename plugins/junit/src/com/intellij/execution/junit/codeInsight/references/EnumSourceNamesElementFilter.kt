// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight.references

import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.psi.*
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NonNls

internal class EnumSourceNamesElementFilter : ElementFilter {
  override fun isAcceptable(element: Any, context: PsiElement?): Boolean {
    val literal = context as? PsiLiteral ?: return false;

    val modeDefaultValue = findModeDefaultValue(literal) ?: return false
    return annotationContainsIncludeExcludeOrDefaultMode(literal, modeDefaultValue)
  }

  override fun isClassAcceptable(hintClass: Class<*>): Boolean {
    return PsiLiteral::class.java.isAssignableFrom(hintClass)
  }

  @NonNls
  override fun toString(): String {
    return "junit5 EnumSource names"
  }

  private fun findModeDefaultValue(literal: PsiLiteral): String? {
    val annotation = PsiTreeUtil.getParentOfType(literal, PsiAnnotation::class.java, false) ?: return null
    return findModeDefaultValue(annotation)
  }

  private fun annotationContainsIncludeExcludeOrDefaultMode(literal: PsiLiteral, modeDefaultValue: String): Boolean {
    val annotationParameters = PsiTreeUtil.getParentOfType(literal, PsiAnnotationParameterList::class.java, false)
                               ?: return false
    return annotationContainsIncludeExcludeOrDefaultMode(annotationParameters, modeDefaultValue)
  }

  private fun findModeDefaultValue(annotation: PsiAnnotation): String? {
    fun resolveModeValue(annotationMethod: PsiAnnotationMethod): String? {
      // junit5 5.0+ has default value for mode specified
      val enumConstant = (annotationMethod.defaultValue as? PsiReferenceExpression)
                           ?.resolve() as? PsiEnumConstant ?: return null
      return enumConstant.name
    }

    val annotationClass = annotation.nameReferenceElement?.resolve() as? PsiClass ?: return null
    return annotationClass
      .methods.asSequence()
      .filterIsInstance(PsiAnnotationMethod::class.java)
      .filter { it.name == "mode" }
      .mapNotNull { resolveModeValue(it) }
      .firstOrNull()
  }

  private fun annotationContainsIncludeExcludeOrDefaultMode(annotationParameters: PsiAnnotationParameterList,
                                                            modeDefaultValue: String): Boolean {
    fun isSupportedMode(mode: String) = mode == "INCLUDE" || mode == "EXCLUDE"

    val modePair = annotationParameters.attributes.firstOrNull { it != null && it.name == "mode" }
    if (modePair != null) {
      val fieldName = (modePair.attributeValue as? JvmAnnotationEnumFieldValue)?.fieldName ?: return false
      return isSupportedMode(fieldName)
    }
    else {
      // no mode explicitly specified, check default value from annotation class definition
      return isSupportedMode(modeDefaultValue)
    }
  }
}
