/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.resolve.references

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.util.SmartList
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult.EMPTY_ARRAY
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrOperatorExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.HardcodedGroovyMethodConstants.*
import org.jetbrains.plugins.groovy.lang.resolve.DependentResolver
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.getMethodCandidates

object GrOperatorResolver : DependentResolver<GrOperatorExpression>() {

  private val operatorNames = mapOf(
    mPLUS to PLUS,
    mMINUS to MINUS,
    mDIV to DIV,
    mSTAR to MULTIPLY,
    mMOD to MOD,
    mSTAR_STAR to POWER,

    mBAND to AND,
    mBOR to OR,
    mBXOR to XOR,

    COMPOSITE_LSHIFT_SIGN to LEFT_SHIFT,
    COMPOSITE_RSHIFT_SIGN to RIGHT_SHIFT,
    COMPOSITE_TRIPLE_SHIFT_SIGN to RIGHT_SHIFT_UNSIGNED,

    mEQUAL to EQUALS,
    mNOT_EQUAL to EQUALS,

    mLT to COMPARE_TO,
    mLE to COMPARE_TO,
    mGT to COMPARE_TO,
    mGE to COMPARE_TO,
    mCOMPARE_TO to COMPARE_TO
  )

  override fun collectDependencies(ref: GrOperatorExpression): Collection<PsiPolyVariantReference>? {
    val result = SmartList<PsiPolyVariantReference>()
    ref.accept(object : PsiRecursiveElementWalkingVisitor() {

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
        if (element is GrOperatorExpression) {
          result.add(element)
        }
      }
    })
    return result
  }

  override fun doResolve(ref: GrOperatorExpression, incomplete: Boolean): Array<out GroovyResolveResult> {
    val operatorName = operatorNames[ref.operator] ?: return EMPTY_ARRAY
    val leftType = ref.leftType ?: return EMPTY_ARRAY
    val rightType = ref.rightType
    return getMethodCandidates(leftType, operatorName, ref, true, incomplete, rightType)
  }
}
