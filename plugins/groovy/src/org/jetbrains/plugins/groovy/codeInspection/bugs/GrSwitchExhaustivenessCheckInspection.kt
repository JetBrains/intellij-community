// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.*
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.getAllPermittedClasses
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
      val conditionalType = switchElement.condition?.type ?: PsiType.NULL
      val patterns = cases.flatMap { it.expressions?.asList() ?: emptyList() }.filterNotNull()
      if (patterns.isEmpty()) {
        return
      }
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
      val clazz = conditionalType.resolve() as? GrTypeDefinition ?: return
      val resolvedPatterns = patterns.mapNotNull { (it as? GrReferenceExpression)?.resolve() }
      if (clazz is GrEnumTypeDefinition) {
        val constants = clazz.enumConstants
        val necessaryConstants = constants.asList() - resolvedPatterns
        insertErrors(switchElement, necessaryConstants.isNotEmpty(), necessaryConstants)
        return
      }
      val resolvedClasses = resolvedPatterns.filterIsInstance<PsiClass>()
      val permittedSubclasses = getAllPermittedClasses(clazz)
      val necessarySubclasses = permittedSubclasses - resolvedClasses
      if (permittedSubclasses.isNotEmpty()) {
        insertErrors(switchElement, necessarySubclasses.isNotEmpty(), necessarySubclasses)
      }
      else {
        val allTypesCovered = resolvedClasses.any { clazz.isInheritor(it, true) }
                              // not sure if we need to enable this by default
                              //&& patterns.any { it is GrLiteral && it.isNullLiteral() }
        insertErrors(switchElement, allTypesCovered, emptyList())
      }
    }

    private fun handlePrimitiveType(switchElement: GrSwitchElement,
                                    conditionalType: PsiPrimitiveType,
                                    patterns: List<GrExpression>) = when (conditionalType) {
      PsiType.BOOLEAN -> {
        val factory = GroovyPsiElementFactory.getInstance(switchElement.project)
        val patternTexts = patterns.map { it.text }
        val trueLiteral = if ("true" !in patternTexts) factory.createLiteralFromValue(true) else null
        val falseLiteral = if ("false" !in patternTexts) factory.createLiteralFromValue(false) else null
        val necessaryPatterns = listOfNotNull(trueLiteral, falseLiteral)
        insertErrors(switchElement, necessaryPatterns.isNotEmpty(), necessaryPatterns)
      }
      PsiType.BYTE, PsiType.SHORT, PsiType.INT, PsiType.LONG -> {
        val definedRanges = glueRange(patterns)
        insertErrors(switchElement, definedRanges.size > 1, emptyList())
        val expectedRange = calculateActualRange(conditionalType)
        if (definedRanges.size == 1) {
          insertErrors(switchElement, definedRanges[0] != expectedRange, emptyList())
        }
        Unit
      }
      else -> {
        insertErrors(switchElement, true, emptyList())
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
            // todo: proper ranges, fix with IDEA-278456
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

    private fun insertErrors(switchElement: GrSwitchElement, reallyInsert: Boolean, missingExpressions : List<PsiElement>) {
      if (reallyInsert) {
        registerError(switchElement.firstChild,
          GroovyBundle.message("inspection.message.switch.expression.does.not.cover.all.possible.outcomes"),
          arrayOf(GrAddMissingCaseSectionsFix(missingExpressions, switchElement)),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }
}