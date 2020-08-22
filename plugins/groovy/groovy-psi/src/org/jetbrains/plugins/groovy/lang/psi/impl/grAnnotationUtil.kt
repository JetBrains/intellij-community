// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList

fun PsiAnnotation.findDeclaredDetachedValue(@NlsSafe attributeName: String?): PsiAnnotationMemberValue? {
  return AnnotationUtil.findDeclaredAttribute(this, attributeName)?.detachedValue
}

internal fun <T : Any> PsiAnnotationMemberValue.getArrayValue(computeValue: (PsiAnnotationMemberValue) -> T?): List<T> {
  return AnnotationUtil.arrayAttributeValues(this).mapNotNull(computeValue)
}

fun PsiAnnotationMemberValue?.booleanValue(): Boolean? = (this as? PsiLiteral)?.value as? Boolean

fun PsiAnnotationMemberValue?.stringValue(): String? = (this as? PsiLiteral)?.value as? String

fun GrModifierList.hasAnnotation(fqn: String): Boolean = annotations.any { it.qualifiedName == fqn }
