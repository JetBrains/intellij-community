// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.*
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReferenceBase

class GrUnaryOperatorReference(element: GrUnaryExpression) : GroovyMethodCallReferenceBase<GrUnaryExpression>(element) {

  override fun getRangeInElement(): TextRange = element.operationToken.textRangeInParent

  override val receiverArgument: Argument? get() = element.operand?.let(::ExpressionArgument)

  override val methodName: String
    get() {
      val tokenType = element.operationTokenType
      return unaryOperatorMethodNames[tokenType] ?: error("unexpected token type: $tokenType")
    }

  override val arguments: Arguments? get() = emptyList()

  companion object {
    private val unaryOperatorMethodNames = mapOf(
      T_NOT to AS_BOOLEAN,
      T_PLUS to POSITIVE,
      T_MINUS to NEGATIVE,
      T_DEC to PREVIOUS,
      T_INC to NEXT,
      T_BNOT to BITWISE_NEGATE
    )
  }
}
