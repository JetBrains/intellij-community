// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.findCall
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder
import org.jetbrains.plugins.groovy.lang.sam.findSingleAbstractSignature

open class ClosureSamParameterEnhancer : AbstractClosureParameterEnhancer() {

  override fun getClosureParameterType(expression: GrFunctionalExpression, index: Int): PsiType? {
    val type = expectedType(expression) as? PsiClassType ?: return null
    val result = type.resolveGenerics()
    val clazz = result.element ?: return null
    val substitutor = result.substitutor
    val sam = findSingleAbstractSignature(clazz) ?: return null
    return sam.parameterTypes.getOrNull(index)?.let(substitutor::substitute)
  }

  private fun expectedType(expression: GrFunctionalExpression): PsiType? {
    val parent = expression.parent
    if (!isCompileStatic(expression)) {
      if (parent is GrSafeCastExpression) {
        return parent.castTypeElement?.type
      }
      else if (parent is GrListOrMap && !parent.isMap) {
        return fromLiteralConstructorArgument(expression, parent)
      }
      val controlFlow = ControlFlowUtils.findControlFlowOwner(expression)
      if (controlFlow != null && ControlFlowUtils.isReturnValue(expression, controlFlow)) {
        return fromReturnStatement(expression)
      }
    }
    return fromMethodCall(expression)
  }

  private fun fromReturnStatement(expression: GrFunctionalExpression): PsiType? = expression.parentOfType<GrMethod>()?.returnType

  private fun fromLiteralConstructorArgument(expression: GrFunctionalExpression, list: GrListOrMap): PsiType? {
    val constructorResult = list.constructorReference?.advancedResolve() as? GroovyMethodResult ?: return null
    return constructorResult.candidate?.argumentMapping?.expectedType(ExpressionArgument(expression))
  }

  private fun fromMethodCall(expression: GrFunctionalExpression): PsiType? {
    val call = findCall(expression) ?: return null
    val variant = call.advancedResolve() as? GroovyMethodResult ?: return null
    val candidate = variant.candidate ?: return null
    val mapping = candidate.argumentMapping ?: return null
    val expectedType = mapping.expectedType(ExpressionArgument(expression)) ?: return null
    val substitutor = substitutorIgnoringClosures(call, candidate, variant)
    return substitutor.substitute(expectedType)
  }

  companion object {
    fun substitutorIgnoringClosures(call: GrCall, candidate: GroovyMethodCandidate, variant: GroovyMethodResult): PsiSubstitutor {
      return GroovyInferenceSessionBuilder(call, candidate, variant.contextSubstitutor)
        .skipClosureIn(call)
        .resolveMode(false)
        .build()
        .inferSubst()
    }
  }
}
