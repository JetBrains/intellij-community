// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyDependentReference
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.*
import org.jetbrains.plugins.groovy.lang.resolve.api.*

class GrOperatorReference(
  element: GrOperatorExpression
) : GroovyMethodCallReferenceBase<GrOperatorExpression>(element),
    GroovyDependentReference {

  override fun getRangeInElement(): TextRange = element.operationToken.textRangeInParent

  override val receiverArgument: Argument?
    get() {
      val operand = when (val element = element) {
        is GrBinaryExpression -> element.leftOperand
        is GrAssignmentExpression -> element.lValue
        else -> return null
      }
      return ExpressionArgument(operand)
    }

  override val methodName: String = binaryOperatorMethodNames[element.operator] ?: error(element.text)

  override val arguments: Arguments?
    get() {
      val operand = when (val element = element) {
        is GrBinaryExpression -> element.rightOperand
        is GrAssignmentExpression -> element.rValue
        else -> null
      }
      val argument = if (operand == null) UnknownArgument else ExpressionArgument(operand)
      return listOf(argument)
    }

  override fun collectDependencies(): MutableCollection<out PsiPolyVariantReference> {
    val result = SmartList<PsiPolyVariantReference>()
    element.accept(object : PsiRecursiveElementWalkingVisitor() {

      override fun visitElement(element: PsiElement) {
        if (element is GrOperatorExpression) {
          super.visitElement(element)
        }
        else if (element is GrParenthesizedExpression) {
          val operand = element.operand
          if (operand != null) super.visitElement(operand)
        }
      }

      override fun elementFinished(element: PsiElement) {
        (element as? GrOperatorExpression)?.reference?.let {
          result.add(it)
        }
      }
    })
    return result
  }

  companion object {

    @JvmStatic
    fun hasOperatorReference(expression: GrOperatorExpression): Boolean = expression.operator in binaryOperatorMethodNames

    private val binaryOperatorMethodNames = mapOf(
      T_PLUS to PLUS,
      T_MINUS to MINUS,
      T_DIV to DIV,
      T_STAR to MULTIPLY,
      T_REM to MOD,
      T_POW to POWER,

      T_BAND to AND,
      T_BOR to OR,
      T_XOR to XOR,

      LEFT_SHIFT_SIGN to LEFT_SHIFT,
      RIGHT_SHIFT_SIGN to RIGHT_SHIFT,
      RIGHT_SHIFT_UNSIGNED_SIGN to RIGHT_SHIFT_UNSIGNED,

      T_EQ to EQUALS,
      T_NEQ to EQUALS,

      T_LT to COMPARE_TO,
      T_LE to COMPARE_TO,
      T_GT to COMPARE_TO,
      T_GE to COMPARE_TO,
      T_COMPARE to COMPARE_TO
    )
  }
}
