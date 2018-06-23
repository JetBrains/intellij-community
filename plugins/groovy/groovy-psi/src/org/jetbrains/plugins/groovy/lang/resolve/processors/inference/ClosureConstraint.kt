// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil.getNonWildcardParameterization
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.sam.findSingleAbstractMethod
import org.jetbrains.plugins.groovy.lang.sam.isSamConversionAllowed

class ClosureConstraint(val closure: GrClosableBlock, val leftType: PsiType) : ConstraintFormula {
  override fun reduce(session: InferenceSession, constraints: MutableList<ConstraintFormula>): Boolean {
    if ((session as GroovyInferenceSession).skipClosureBlock) {
      //TODO:add explicit typed closure constraints
    } else {
      if (leftType !is PsiClassType) return true
      val closureReturnType = closure.returnType ?: return true
      if (closureReturnType == PsiType.VOID) {
        return true
      }
      if ( TypesUtil.isClassType(leftType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
        val parameters = leftType.parameters
        if (parameters.size != 1) return true
        constraints.add(TypeConstraint(parameters[0], closureReturnType, closure))
      } else {
        val samReturnType = callSamReturnType() ?: return true
        constraints.add(TypeConstraint(samReturnType, closureReturnType, closure))
      }
    }
    return true
  }

  private fun callSamReturnType(): PsiType? {
    if (isSamConversionAllowed(closure)) {
      val groundType = (leftType as? PsiClassType)?.let { getNonWildcardParameterization(it) } ?: return null
      val resolveResult = (groundType as PsiClassType).resolveGenerics()

      val samClass = resolveResult.element ?: return null

      val sam = findSingleAbstractMethod(samClass) ?: return null

      return resolveResult.substitutor.substitute(sam.returnType)
    }
    return null
  }

  override fun apply(substitutor: PsiSubstitutor, cache: Boolean) {}
}
