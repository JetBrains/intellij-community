// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.getArguments
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.*

class EmptyRangeInspection : AbstractRangeInspection() {
    override fun visitRangeTo(expression: KtExpression, context: BindingContext, holder: ProblemsHolder) {
        expression.startAndEndValueSignedOrNull(context)?.let { (startValue, endValue) ->
            if (startValue > endValue) holder.registerProblem(expression, downTo = true)
        }
        expression.startAndEndValueUnSignedOrNull(context)?.let { (startValue, endValue) ->
            if (startValue > endValue) holder.registerProblem(expression, downTo = true)
        }
    }

    override fun visitUntil(expression: KtExpression, context: BindingContext, holder: ProblemsHolder) {
        expression.startAndEndValueSignedOrNull(context)?.let { (startValue, endValue) ->
            when {
                startValue > endValue -> holder.registerProblem(expression, downTo = true)
                startValue == endValue -> holder.registerProblem(expression, downTo = false)
            }
        }
        expression.startAndEndValueUnSignedOrNull(context)?.let { (startValue, endValue) ->
            when {
                startValue > endValue -> holder.registerProblem(expression, downTo = true)
                startValue == endValue -> holder.registerProblem(expression, downTo = false)
            }
        }
    }

    override fun visitDownTo(expression: KtExpression, context: BindingContext, holder: ProblemsHolder) {
        expression.startAndEndValueSignedOrNull(context)?.let { (startValue, endValue) ->
            if (startValue < endValue) holder.registerProblem(expression, downTo = false)
        }
        expression.startAndEndValueUnSignedOrNull(context)?.let { (startValue, endValue) ->
            if (startValue < endValue) holder.registerProblem(expression, downTo = false)
        }
    }

    private fun ProblemsHolder.registerProblem(expression: KtExpression, downTo: Boolean) {
        val (functionName, operator) = if (downTo) "downTo" to "downTo" else "rangeTo" to ".."
        registerProblem(
            expression,
            KotlinBundle.message("this.range.is.empty.did.you.mean.to.use.0", functionName),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            ReplaceFix(operator)
        )
    }

    private class ReplaceFix(private val rangeOperator: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("replace.with.0", rangeOperator)

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtExpression ?: return
            val (left, right) = element.getArguments() ?: return
            if (left == null || right == null) return

            element.replace(KtPsiFactory(element).createExpressionByPattern("$0 $rangeOperator $1", left, right))
        }
    }

    private fun KtExpression.startAndEndValueSignedOrNull(context: BindingContext): Pair<Long, Long>? {
        val (start, end) = getArguments() ?: return null
        if (start?.isSignedValueConstant(context) == false) return null
        val startValue = start?.longValueOrNull(context) ?: return null
        val endValue = end?.longValueOrNull(context) ?: return null
        return startValue to endValue
    }

    private fun KtExpression.startAndEndValueUnSignedOrNull(context: BindingContext): Pair<ULong, ULong>? {
        val (start, end) = getArguments() ?: return null
        if (start?.isSignedValueConstant(context) == true) return null
        val startValue = start?.uLongValueOrNull(context) ?: return null
        val endValue = end?.uLongValueOrNull(context) ?: return null
        return startValue to endValue
    }

    private fun KtExpression.isSignedValueConstant(context: BindingContext) = constantValueOrNull(context) is IntegerValueConstant<*>

    private fun KtExpression.longValueOrNull(context: BindingContext): Long? {
        return when (val constantValue = constantValueOrNull(context)?.value) {
            is Number -> constantValue.toLong()
            is Char -> constantValue.code.toLong()
            else -> null
        }
    }

    private fun KtExpression.uLongValueOrNull(context: BindingContext): ULong? {
        return when (val constantValue = constantValueOrNull(context)) {
            is UByteValue -> constantValue.value.toUByte().toULong()
            is UShortValue -> constantValue.value.toUShort().toULong()
            is UIntValue -> constantValue.value.toUInt().toULong()
            is ULongValue -> constantValue.value.toULong()
            else -> null
        }
    }
}