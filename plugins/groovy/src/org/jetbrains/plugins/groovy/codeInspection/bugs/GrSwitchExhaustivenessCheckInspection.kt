// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.bugs

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.*
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression.BoundaryType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.psi.util.isNullLiteral
import javax.swing.JComponent
import kotlin.math.max

class GrSwitchExhaustivenessCheckInspection : BaseInspection() {

  private var shouldReportNulls: Boolean = false

  @TestOnly
  fun enableNullCheck() {
    shouldReportNulls = true
  }

  override fun createGroovyOptionsPanel(): JComponent = panel {
    row {
      checkBox(GroovyBundle.message("checkbox.report.unmatched.null")).bindSelected(::shouldReportNulls)
    }
  }

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
      val clazz = conditionalType.resolve() ?: return
      val resolvedPatterns = patterns.mapNotNull { (it as? GrReferenceExpression)?.resolve() }
      val elementsToInsert = if (clazz.isEnum) {
        checkEnum(clazz, resolvedPatterns)
      }
      else {
        checkPatternMatchingOnType(clazz, resolvedPatterns.filterIsInstance<PsiClass>())
      }
      val nullElement = if (shouldReportNulls && !(patterns.any { it is GrLiteral && it.isNullLiteral() })) {
        listOf(GroovyPsiElementFactory.getInstance(switchElement.project).createLiteralFromValue(null))
      }
      else {
        emptyList()
      }
      val allElementsToInsert = (elementsToInsert + nullElement)
      insertErrors(switchElement, allElementsToInsert.isNotEmpty(), allElementsToInsert)
    }

    private fun checkEnum(clazz: PsiClass, existingPatterns: List<PsiElement>): List<PsiElement> {
      val constants = clazz.allFields.filterIsInstance<PsiEnumConstant>()
      val unwrappedPatterns = existingPatterns.flatMap { if (it is GrRangeExpression) unwrapEnumRange(constants, it) else listOf(it) }
      return constants - unwrappedPatterns
    }

    private fun unwrapEnumRange(allConstants : List<PsiEnumConstant>, range : GrRangeExpression) : List<PsiEnumConstant> {
      val leftBound = (range.from as? GrReferenceExpression)?.resolve() as? PsiEnumConstant ?: return emptyList()
      val rightBound = (range.to as? GrReferenceExpression)?.resolve() as? PsiEnumConstant ?: return emptyList()
      val boundaryType = range.boundaryType ?: return emptyList()
      val coveredConstants = mutableListOf<PsiEnumConstant>()
      var startCollecting = false
      for (constant in allConstants) {
        if (constant == rightBound) {
          if (boundaryType == BoundaryType.CLOSED || boundaryType == BoundaryType.LEFT_OPEN) {
            coveredConstants.add(constant)
          }
          break
        }
        if (startCollecting) {
          coveredConstants.add(constant)
        }
        if (constant == leftBound) {
          startCollecting = true
          if (boundaryType == BoundaryType.CLOSED || boundaryType == BoundaryType.RIGHT_OPEN) {
            coveredConstants.add(constant)
          }
        }
      }
      return coveredConstants
    }

    private fun checkPatternMatchingOnType(clazz: PsiClass, resolvedClasses: List<PsiClass>): List<PsiElement> {
      if (resolvedClasses.any { clazz === it || clazz.isInheritor(it, true) }) {
        return emptyList()
      }
      if (clazz is GrTypeDefinition) {
        val permittedSubclasses = PsiUtil.getAllPermittedClassesJvmAware(clazz)
        if (permittedSubclasses.isNotEmpty() && (clazz.isInterface || clazz.hasModifierProperty(PsiModifier.ABSTRACT))) {
          return permittedSubclasses.flatMap { checkPatternMatchingOnType(it, resolvedClasses) }
        }
      }
      return listOf(clazz)
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
          val definedRange = definedRanges.single()
          insertErrors(switchElement, definedRange.first > expectedRange.first || definedRange.second < expectedRange.second, emptyList())
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
      // both-inclusive
      data class Range(val left: Long, val right: Long)

      val ranges = mutableListOf<Range>()
      val evaluator = JavaPsiFacade.getInstance(patterns[0].project).constantEvaluationHelper
      for (pattern in patterns) {
        if (pattern is GrLiteral) {
          val evaluated = evaluator.computeConstantExpression(pattern) as? Number
          evaluated?.toLong()?.let { ranges.add(Range(it, it)) }
        }
        else if (pattern is GrRangeExpression) {
          val left = evaluator.computeConstantExpression(pattern.from) as? Number
          val right = evaluator.computeConstantExpression(pattern.to) as? Number
          if (left != null && right != null) {
            val boundaryType = pattern.boundaryType
            val leftDelta = if (boundaryType == BoundaryType.LEFT_OPEN || boundaryType == BoundaryType.BOTH_OPEN) 1 else 0
            val rightDelta = if (boundaryType == BoundaryType.RIGHT_OPEN || boundaryType == BoundaryType.BOTH_OPEN) 1 else 0
            ranges.add(Range(left.toLong() + leftDelta, right.toLong() - rightDelta))
          }
        }
      }
      ranges.sortBy { it.left }
      val collapsedRanges = mutableListOf<Pair<Long, Long>>()
      for (range in ranges) {
        if (collapsedRanges.isEmpty() || collapsedRanges.last().second < range.left - 1) {
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