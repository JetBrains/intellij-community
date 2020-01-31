// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature
import org.jetbrains.plugins.groovy.util.recursionAwareLazy

internal class GroovyPsiClosureType(val closure: GrFunctionalExpression) : GroovyClosureType(closure) {

  override fun returnType(arguments: Arguments?): PsiType? = closure.returnType

  override val signatures: List<CallSignature<*>> by recursionAwareLazy(::doGetSignatures)

  private fun doGetSignatures(): List<CallSignature<*>> {
    val closure: GrFunctionalExpression = closure
    val parameters: Array<out GrParameter> = closure.allParameters
    val optionalParameterCount: Int = parameters.count(GrParameter::isOptional)
    return (0..optionalParameterCount).map {
      FunctionalSignature(closure, it)
    }
  }
}
