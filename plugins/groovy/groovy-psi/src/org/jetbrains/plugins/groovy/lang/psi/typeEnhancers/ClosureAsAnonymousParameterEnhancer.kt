// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.findCall
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder
import org.jetbrains.plugins.groovy.lang.sam.findSingleAbstractSignature

open class ClosureAsAnonymousParameterEnhancer : AbstractClosureParameterEnhancer() {

  override fun getClosureParameterType(closure: GrClosableBlock, index: Int): PsiType? {
    val type = expectedType(closure) as? PsiClassType ?: return null
    val result = type.resolveGenerics()
    val clazz = result.element ?: return null
    val substitutor = result.substitutor
    val sam = findSingleAbstractSignature(clazz) ?: return null
    return sam.parameterTypes.getOrNull(index)?.let(substitutor::substitute)?.let(::unwrapBound)
  }

  private fun expectedType(closure: GrClosableBlock): PsiType? {
    val parent = closure.parent
    if (parent is GrSafeCastExpression) {
      return parent.castTypeElement?.type
    }
    else {
      return fromMethodCall(closure)
    }
  }

  private fun fromMethodCall(closure: GrClosableBlock): PsiType? {
    val call = findCall(closure) ?: return null
    val variant = call.advancedResolve() as? GroovyMethodResult ?: return null
    val candidate = variant.candidate ?: return null
    val mapping = candidate.argumentMapping ?: return null
    val expectedType = mapping.expectedType(ExpressionArgument(closure)) ?: return null
    val substitutor = GroovyInferenceSessionBuilder(call, candidate)
      .skipClosureIn(call)
      .resolveMode(false)
      .build()
      .inferSubst()
    return substitutor.substitute(expectedType)
  }

  private fun unwrapBound(type: PsiType): PsiType? {
    return if (type is PsiWildcardType && type.isSuper) {
      type.bound
    }
    else {
      type
    }
  }
}
