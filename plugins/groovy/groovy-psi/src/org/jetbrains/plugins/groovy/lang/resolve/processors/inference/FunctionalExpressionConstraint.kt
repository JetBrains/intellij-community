// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.sam.processSAMConversion
import org.jetbrains.plugins.groovy.lang.typing.GroovyClosureType

class FunctionalExpressionConstraint(private val expression: GrFunctionalExpression,
                                     private val leftType: PsiType) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<in ConstraintFormula>): Boolean {
    if (leftType !is PsiClassType) return true
    val returnType by lazy(LazyThreadSafetyMode.NONE) {
      expression.returnType
    }
    if (TypesUtil.isClassType(leftType, GROOVY_LANG_CLOSURE)) {
      val parameters = leftType.parameters
      if (parameters.size != 1) return true
      if (returnType == null || returnType == PsiType.VOID) {
        return true
      }
      constraints.add(TypeConstraint(parameters[0], returnType, expression))
    }
    else {
      val closureType = expression.type as? GroovyClosureType ?: return true
      constraints += processSAMConversion(leftType, closureType, expression)
    }
    return true
  }
}