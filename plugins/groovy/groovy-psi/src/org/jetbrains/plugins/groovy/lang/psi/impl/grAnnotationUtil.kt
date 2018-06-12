// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList

fun PsiAnnotation.findDeclaredDetachedValue(attributeName: String?): PsiAnnotationMemberValue? {
  return AnnotationUtil.findDeclaredAttribute(this, attributeName)?.detachedValue
}

internal fun <T : Any> PsiAnnotationMemberValue.getArrayValue(computeValue: (PsiAnnotationMemberValue) -> T?): List<T> {
  return AnnotationUtil.arrayAttributeValues(this).mapNotNull(computeValue)
}

fun PsiAnnotationMemberValue?.booleanValue() = (this as? PsiLiteral)?.value as? Boolean

fun PsiAnnotationMemberValue?.stringValue() = (this as? PsiLiteral)?.value as? String

fun GrModifierList.hasAnnotation(fqn: String) = annotations.any { it.qualifiedName == fqn }
