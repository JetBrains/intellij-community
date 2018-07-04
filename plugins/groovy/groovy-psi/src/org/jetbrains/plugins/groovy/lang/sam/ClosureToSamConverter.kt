// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.sam

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.ApplicableTo.*
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE

class ClosureToSamConverter : GrTypeConverter() {

  private val myPositions = setOf(ASSIGNMENT, RETURN_VALUE, METHOD_PARAMETER)

  override fun isApplicableTo(position: ApplicableTo): Boolean = position in myPositions

  override fun isConvertibleEx(targetType: PsiType, actualType: PsiType, context: GroovyPsiElement,
                               currentPosition: ApplicableTo): ConversionResult? {
    if (targetType !is PsiClassType || (actualType !is GrClosureType && !actualType.equalsToText(GROOVY_LANG_CLOSURE))) return null
    if (!isSamConversionAllowed(context)) return null

    val result = targetType.resolveGenerics()
    val targetClass = result.element ?: return null

    val targetFqn = targetClass.qualifiedName ?: return null // anonymous classes has no fqn
    if (targetFqn == GROOVY_LANG_CLOSURE) return null

    findSingleAbstractSignature(targetClass) ?: return null
    return ConversionResult.OK
  }
}
