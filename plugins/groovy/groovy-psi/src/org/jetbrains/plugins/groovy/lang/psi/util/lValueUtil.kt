// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GroovyLValueUtil")

package org.jetbrains.plugins.groovy.lang.psi.util

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTuple
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.UnknownArgument

/**
 * The expression is a rValue when it is in rValue position or it's a lValue of operator assignment.
 */
fun GrExpression.isRValue(): Boolean {
  val (parent, lastParent) = skipParentsOfType<GrParenthesizedExpression>() ?: return true
  return parent !is GrAssignmentExpression || lastParent != parent.lValue || parent.isOperatorAssignment
}

/**
 * The expression is a lValue when it's on the left of whatever assignment.
 */
fun GrExpression.isLValue(): Boolean {
  val parent = parent
  return when (parent) {
    is GrTuple -> true
    is GrAssignmentExpression -> this == parent.lValue
    else -> false
  }
}

class RValue(val argument: Argument)

/**
 * @return non-null result iff this expression is an l-value
 */
fun GrExpression.getRValue(): RValue? {
  val parent = parent
  return when {
    parent is GrTuple -> RValue(UnknownArgument)
    parent is GrAssignmentExpression && parent.lValue === this -> {
      val argument = if (parent.isOperatorAssignment) {
        ExpressionArgument(parent)
      }
      else {
        parent.rValue?.let(::ExpressionArgument) ?: UnknownArgument
      }
      RValue(argument)
    }
    else -> null
  }
}
