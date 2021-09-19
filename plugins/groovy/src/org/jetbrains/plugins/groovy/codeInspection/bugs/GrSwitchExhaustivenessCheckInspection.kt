// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import kotlin.math.max

class GrSwitchExhaustivenessCheckInspection : BaseInspection() {

  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
    override fun visitSwitchExpression(switchExpression: GrSwitchExpression) {
      visitSwitchElement(switchExpression)
      super.visitSwitchExpression(switchExpression)
    }

    private fun visitSwitchElement(switchElement: GrSwitchElement) {
      val cases = switchElement.caseSections
      if (cases.any { it.isDefault }) {
        return
      }
      val conditionalType = switchElement.condition?.type ?: return
      val patterns = cases.flatMap { it.expressions?.asList() ?: emptyList() }.filterNotNull()
      when (conditionalType) {
        is PsiPrimitiveType -> handlePrimitiveType(switchElement, conditionalType, patterns)
        is PsiClassType -> {
          val unboxed = PsiPrimitiveType.getUnboxedType(conditionalType)
          if (unboxed != null) {
            handlePrimitiveType(switchElement, unboxed, patterns)
          }
          else {
            handleClassType(switchElement, conditionalType, patterns)
          }
        }
      }
    }


    private fun handleClassType(switchElement: GrSwitchElement, conditionalType: PsiClassType, patterns: List<GrExpression>) {
      // check enums and sealed classes
    }

    private fun handlePrimitiveType(switchElement: GrSwitchElement,
                                    conditionalType: PsiPrimitiveType,
                                    patterns: List<GrExpression>) = when (conditionalType) {
      PsiType.BOOLEAN -> insertErrors(switchElement, !patterns.map { it.text }.containsAll(listOf("true", "false")))
      PsiType.BYTE, PsiType.SHORT, PsiType.INT, PsiType.LONG -> {
        val definedRanges = glueRange(patterns)
        insertErrors(switchElement, definedRanges.size > 1)
        val expectedRange = calculateActualRange(conditionalType)
        insertErrors(switchElement, definedRanges[0] != expectedRange)
      }
      else -> {
      }
    }

    private fun calculateActualRange(conditionalType: PsiPrimitiveType): Pair<Long, Long> = when (conditionalType) {
      PsiType.BYTE -> Byte.MIN_VALUE.toLong() to Byte.MAX_VALUE.toLong()
      PsiType.SHORT -> Short.MIN_VALUE.toLong() to Short.MAX_VALUE.toLong()
      PsiType.INT -> Int.MIN_VALUE.toLong() to Int.MAX_VALUE.toLong()
      PsiType.LONG -> Long.MIN_VALUE to Long.MAX_VALUE
      else -> error("unreachable")
    }

    private fun glueRange(patterns: List<GrExpression>): List<Pair<Long, Long>> {
      data class Range(val left: Long, val right: Long)

      val ranges = mutableListOf<Range>()
      val evaluator = JavaPsiFacade.getInstance(patterns[0].project).constantEvaluationHelper
      for (pattern in patterns) {
        if (pattern is GrLiteral) {
          val evaluated = evaluator.computeConstantExpression(pattern) as? Number
          evaluated?.toLong()?.let { ranges.add(Range(it, it + 1)) }
        }
        else if (pattern is GrRangeExpression) {
          val left = evaluator.computeConstantExpression(pattern.from) as? Number
          val right = evaluator.computeConstantExpression(pattern.to) as? Number
          if (left != null && right != null) {
            // todo: proper ranges
            ranges.add(Range(left.toLong(), right.toLong()))
          }
        }
      }
      ranges.sortBy { it.left }
      val collapsedRanges = mutableListOf<Pair<Long, Long>>()
      for (range in ranges) {
        if (collapsedRanges.isEmpty() || collapsedRanges.last().second < range.left) {
          collapsedRanges.add(range.left to range.right)
        }
        else {
          val lastRange = collapsedRanges.removeLast()
          collapsedRanges.add(lastRange.first to max(lastRange.second, range.right))
        }
      }
      return collapsedRanges
    }

    private fun insertErrors(switchElement: GrSwitchElement, reallyInsert: Boolean) {
      if (reallyInsert) {
        registerError(switchElement.firstChild,
          GroovyBundle.message("inspection.message.switch.expression.does.not.cover.all.possible.outcomes"), emptyArray(),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }
}