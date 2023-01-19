// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.collections.isIterable
import org.jetbrains.kotlin.idea.intentions.getArguments
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType.*
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType

/**
 * Tests:
 * [org.jetbrains.kotlin.idea.codeInsight.InspectionTestGenerated.Inspections.testEmptyRange_inspectionData_Inspections_test]
 * [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.EmptyRange]
 */
class EmptyRangeInspection : AbstractRangeInspection() {
    override fun visitRange(range: KtExpression, context: Lazy<BindingContext>, type: RangeKtExpressionType, holder: ProblemsHolder) =
        visitRangeImpl<Nothing>(range, context, type, holder)

    private fun <T> visitRangeImpl(
        range: KtExpression,
        context: Lazy<BindingContext>,
        type: RangeKtExpressionType,
        holder: ProblemsHolder
    ) where T : Comparable<T> {
        when (type) {
            RANGE_TO -> range.getComparableArguments<T>(context)?.let { (startValue, endValue) ->
                if (startValue > endValue) holder.registerProblem(range, context, downTo = true)
            }

            UNTIL, RANGE_UNTIL -> range.getComparableArguments<T>(context)?.let { (startValue, endValue) ->
                when {
                    startValue > endValue -> holder.registerProblem(range, context, downTo = true)
                    startValue == endValue -> holder.registerProblem(range, context, downTo = false)
                }
            }

            DOWN_TO -> range.getComparableArguments<T>(context)?.let { (startValue, endValue) ->
                if (startValue < endValue) holder.registerProblem(range, context, downTo = false)
            }
        }
    }

    private fun ProblemsHolder.registerProblem(expression: KtExpression, context: Lazy<BindingContext>, downTo: Boolean) {
        val (msg, fixes) =
            if (!downTo || expression.getType(context.value)?.isIterable() == true) {
                val (functionName, operator) = if (downTo) "downTo" to "downTo" else "rangeTo" to ".."
                KotlinBundle.message("this.range.is.empty.did.you.mean.to.use.0", functionName) to arrayOf(ReplaceFix(operator))
            } else {
                KotlinBundle.message("this.range.is.empty") to emptyArray<LocalQuickFix>()
            }
        registerProblem(
            expression,
            msg,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            *fixes
        )
    }

    private class ReplaceFix(private val rangeOperator: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("replace.with.0", rangeOperator)

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtExpression ?: return
            val (left, right) = element.getArguments() ?: return
            if (left == null || right == null) return

            element.replace(KtPsiFactory(project).createExpressionByPattern("$0 $rangeOperator $1", left, right))
        }
    }

    private fun <T> KtExpression.getComparableArguments(context: Lazy<BindingContext>): Pair<T, T>? where T : Comparable<T> {
        val (start, end) = getArguments() ?: return null
        @Suppress("UNCHECKED_CAST")
        fun KtExpression.value() = constantValueOrNull(context.value)?.boxedValue()
            // Because it's possible to write such things `2L..0`
            ?.let { if (it is Number && it !is Double && it !is Float) it.toLong() else it } as? T

        val startValue = start?.value() ?: return null
        val endValue = end?.value() ?: return null
        if (startValue::class != endValue::class) return null
        return startValue to endValue
    }
}