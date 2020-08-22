// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.sam

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.Position.*
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.typing.GroovyClosureType

class ClosureToSamConverter : GrTypeConverter() {

  private val myPositions = setOf(ASSIGNMENT, RETURN_VALUE, METHOD_PARAMETER)

  override fun isApplicableTo(position: Position): Boolean = position in myPositions

  override fun isConvertible(targetType: PsiType,
                             actualType: PsiType,
                             position: Position,
                             context: GroovyPsiElement): ConversionResult? {
    if (targetType !is PsiClassType ||
        actualType !is GroovyClosureType && !TypesUtil.isClassType(actualType, GROOVY_LANG_CLOSURE)) return null

    if (!isSamConversionAllowed(context)) return null

    val result = targetType.resolveGenerics()
    val targetClass = result.element ?: return null

    val targetFqn = targetClass.qualifiedName ?: return null // anonymous classes has no fqn
    if (targetFqn == GROOVY_LANG_CLOSURE) return null

    findSingleAbstractSignature(targetClass) ?: return null
    return ConversionResult.OK
  }

  override fun reduceTypeConstraint(leftType: PsiType,
                                    rightType: PsiType,
                                    position: Position,
                                    context: PsiElement): List<ConstraintFormula>? {
    if (rightType !is GroovyClosureType) return null
    return processSAMConversion(leftType, rightType, context)
  }
}
