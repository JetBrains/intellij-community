// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.spock

import com.intellij.psi.util.parents
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.RIGHT_SHIFT_SIGN
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.RIGHT_SHIFT_UNSIGNED_SIGN
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isExpressionStatement

fun GrExpression.isInteractionPart(): Boolean {
  return isInteractionDown() &&
         isInteractionUp() ||
         isInteractionCallUp()
}

private fun GrExpression.isInteractionDown(): Boolean {
  return this is GrBinaryExpression &&
         isInteractionDown()
}

private fun GrBinaryExpression.isInteractionDown(): Boolean {
  return isInteractionWithResponseDown() ||
         isInteractionWithCardinalityDown()
}

private fun GrBinaryExpression.isInteractionWithResponseDown(): Boolean {
  return isRightShift() &&
         (leftOperand.isInteractionDown() ||
          leftOperand.isInteractionCall())
}

private fun GrBinaryExpression.isInteractionWithCardinalityDown(): Boolean {
  return isMultiplication() &&
         rightOperand.isInteractionCall()
}

/**
 * org.spockframework.compiler.InteractionRewriter#parseCall
 */
private fun GrExpression?.isInteractionCall(): Boolean {
  return this is GrReferenceExpression || this is GrMethodCall || this is GrNewExpression
}

private fun GrExpression.isInteractionUp(): Boolean {
  for ((lastParent, parent) in parents(true).zipWithNext()) {
    if (parent is GrBinaryExpression) {
      if (parent.leftOperand !== lastParent || !parent.isRightShift()) {
        return false
      }
      else {
        continue
      }
    }
    else {
      return isExpressionStatement(lastParent)
    }
  }
  return false
}

private fun GrExpression.isInteractionCallUp(): Boolean {
  return isInteractionCall() && parent.let {
    it is GrBinaryExpression && it.rightOperand === this && it.isMultiplication() && it.isInteractionUp()
  }
}

private fun GrBinaryExpression.isRightShift(): Boolean = operationTokenType.let {
  it === RIGHT_SHIFT_SIGN ||
  it === RIGHT_SHIFT_UNSIGNED_SIGN
}

private fun GrBinaryExpression.isMultiplication(): Boolean {
  return operationTokenType === GroovyElementTypes.T_STAR
}
