// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil.getNonWildcardParameterization
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.sam.findSingleAbstractMethod
import org.jetbrains.plugins.groovy.lang.sam.isSamConversionAllowed

class FunctionalExpressionConstraint(private val expression: GrFunctionalExpression, private val leftType: PsiType) : GrConstraintFormula() {

  override fun reduce(session: GroovyInferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    if (leftType !is PsiClassType) return true
    val returnType by lazy(LazyThreadSafetyMode.NONE) {
      expression.returnType
    }
    if (TypesUtil.isClassType(leftType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
      val parameters = leftType.parameters
      if (parameters.size != 1) return true
      if (returnType == null || returnType == PsiType.VOID) {
        return true
      }
      constraints.add(TypeConstraint(parameters[0], returnType, expression))
    }
    else {
      val samReturnType = callSamReturnType()
      if (samReturnType == null) {
        constraints.add(TypeConstraint(leftType, TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, expression), expression))
        return true
      }
      if (returnType == null || returnType == PsiType.VOID) {
        return true
      }
      constraints.add(TypeConstraint(samReturnType, returnType, expression))
    }
    return true
  }

  private fun callSamReturnType(): PsiType? {
    if (isSamConversionAllowed(expression)) {
      val groundType = (leftType as? PsiClassType)?.let { getNonWildcardParameterization(it) } ?: return null
      val resolveResult = (groundType as PsiClassType).resolveGenerics()

      val samClass = resolveResult.element ?: return null

      val sam = findSingleAbstractMethod(samClass) ?: return null

      return resolveResult.substitutor.substitute(sam.returnType)
    }
    return null
  }
}
