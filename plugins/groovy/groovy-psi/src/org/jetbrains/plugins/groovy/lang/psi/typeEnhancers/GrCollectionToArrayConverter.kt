// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTION
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil.isInheritor
import com.intellij.psi.util.PsiUtil.substituteTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil

class GrCollectionToArrayConverter : GrTypeConverter() {

  override fun isApplicableTo(position: Position): Boolean = position == Position.ASSIGNMENT

  override fun isConvertible(targetType: PsiType,
                             actualType: PsiType,
                             position: Position,
                             context: GroovyPsiElement): ConversionResult? {
    if (targetType !is PsiArrayType) return null
    if (!isInheritor(actualType, JAVA_UTIL_COLLECTION)) return null
    val left = targetType.componentType
    val right = substituteTypeParameter(actualType, JAVA_UTIL_COLLECTION, 0, false) ?: return null
    return TypesUtil.canAssign(left, right, context, Position.ASSIGNMENT)
  }
}
