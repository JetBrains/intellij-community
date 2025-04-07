// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class SuspiciousEqualsCombinationInspection : KotlinApplicableInspectionBase<KtBinaryExpression, Unit>() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = binaryExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        if (element.parent is KtBinaryExpression) return false
        val operands = element.parseBinary()
        val (eqEqOperands, eqEqEqOperands) = operands
        val eqeq = eqEqOperands.map { it.text }
        val eqeqeq = eqEqEqOperands.map { it.text }
        return eqeq.intersect(eqeqeq).isNotEmpty()
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit = Unit

    override fun InspectionManager.createProblemDescriptor(
        element: KtBinaryExpression,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor = createProblemDescriptor(
        /* psiElement = */ element,
        /* rangeInElement = */ rangeInElement,
        /* descriptionTemplate = */ KotlinBundle.message("suspicious.combination.of.and"),
        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */ onTheFly,
    )

    private fun KtBinaryExpression.parseBinary(pair: ComparisonOperands = ComparisonOperands()): ComparisonOperands {
        when (operationToken) {
            KtTokens.EQEQ, KtTokens.EXCLEQ -> {
                if (!left.isNullExpression() && !right.isNullExpression()) {
                    (left as? KtNameReferenceExpression)?.let(pair.eqEqOperands::add)
                    (right as? KtNameReferenceExpression)?.let(pair.eqEqOperands::add)
                }
            }
            KtTokens.EQEQEQ, KtTokens.EXCLEQEQEQ -> {
                if (!left.isNullExpression() && !right.isNullExpression()) {
                    (left as? KtNameReferenceExpression)?.let(pair.eqEqEqOperands::add)
                    (right as? KtNameReferenceExpression)?.let(pair.eqEqEqOperands::add)
                }
            }
            KtTokens.ANDAND, KtTokens.OROR -> {
                right?.parseExpression(pair)
                left?.parseExpression(pair)
            }
        }
        return pair
    }

    private fun KtExpression.parseExpression(pair: ComparisonOperands) {
        when (this) {
            is KtBinaryExpression -> parseBinary(pair)
            is KtParenthesizedExpression -> expression?.parseExpression(pair)
            is KtPrefixExpression -> if (operationToken == KtTokens.EXCL) baseExpression?.parseExpression(pair)
        }
    }
}

private data class ComparisonOperands(
    val eqEqOperands: MutableList<KtExpression> = mutableListOf(),
    val eqEqEqOperands: MutableList<KtExpression> = mutableListOf(),
)
