// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.codeInsight.hints.RangeKtExpressionType
import org.jetbrains.kotlin.idea.codeInsight.hints.getRangeBinaryExpressionType
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.AbstractRangeInspection.ContextWrapper
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

abstract class AbstractRangeInspection<C : Any> : KotlinApplicableInspectionBase.Simple<KtExpression, ContextWrapper<C>>() {
    sealed class RangeExpression() {
        abstract val expression: KtExpression
        abstract val type: RangeKtExpressionType

        abstract val arguments: Pair<KtExpression?, KtExpression?>
    }

    data class BinaryRangeExpression(
        override val expression: KtBinaryExpression,
        override val type: RangeKtExpressionType,
    ) : RangeExpression() {
        override val arguments: Pair<KtExpression?, KtExpression?>
            get() = expression.left to expression.right
    }

    data class DotQualifiedRangeExpression(
        override val expression: KtDotQualifiedExpression,
        override val type: RangeKtExpressionType,
    ) : RangeExpression() {
        override val arguments: Pair<KtExpression, KtExpression?>
            get() {
                val right = expression.callExpression?.valueArguments?.singleOrNull()?.getArgumentExpression()
                return expression.receiverExpression to right
            }
    }

    fun rangeExpressionByPsi(expression: KtExpression): RangeExpression? {
        val expressionType = getRangeBinaryExpressionType(expression) ?: return null
        return when (expression) {
            is KtBinaryExpression -> BinaryRangeExpression(expression, expressionType)
            is KtDotQualifiedExpression -> DotQualifiedRangeExpression(expression, expressionType)
            else -> null
        }
    }

    fun KaSession.rangeExpressionByAnalyze(expression: KtExpression): RangeExpression? =
        rangeExpressionByPsi(expression)?.takeIf {
            val call = expression.resolveToCall()?.singleFunctionCallOrNull()
            val packageName = call?.symbol?.callableId?.packageName
            packageName != null && packageName.startsWith(Name.identifier("kotlin"))
        }
    data class ContextWrapper<C : Any>(
        val range: RangeExpression,
        val wrappedContext: C,
    )

    abstract fun getProblemDescription(
        range: RangeExpression,
        context: C,
    ): @InspectionMessage String

    final override fun getProblemDescription(
        element: KtExpression,
        context: ContextWrapper<C>
    ): @InspectionMessage String {
        check(context.range.expression == element)
        return getProblemDescription(context.range, context.wrappedContext)
    }

    abstract fun createQuickFix(
        range: RangeExpression,
        context: C,
    ): KotlinModCommandQuickFix<KtExpression>

    final override fun createQuickFix(
        element: KtExpression,
        context: ContextWrapper<C>
    ): KotlinModCommandQuickFix<KtExpression> {
        check(context.range.expression == element)
        return createQuickFix(context.range, context.wrappedContext)
    }

    abstract fun isApplicableByPsi(range: RangeExpression): Boolean

    final override fun isApplicableByPsi(element: KtExpression): Boolean {
        val range = rangeExpressionByPsi(element)
        return range != null && isApplicableByPsi(range)
    }

    abstract fun KaSession.prepareContext(range: RangeExpression): C?

    final override fun KaSession.prepareContext(element: KtExpression): ContextWrapper<C>? {
        val range = rangeExpressionByAnalyze(element) ?: return null
        val context = prepareContext(range) ?: return null
        return ContextWrapper(range, context)
    }

    final override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }
}