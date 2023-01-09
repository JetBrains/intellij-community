// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType.*
import org.jetbrains.kotlin.idea.inspections.ReplaceUntilWithRangeUntilInspection.Companion.isPossibleToUseRangeUntil
import org.jetbrains.kotlin.idea.intentions.getArguments
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isDouble
import org.jetbrains.kotlin.types.typeUtil.isFloat
import org.jetbrains.kotlin.types.typeUtil.isPrimitiveNumberType

sealed class AbstractReplaceRangeToWithRangeUntilInspection : AbstractRangeInspection() {
    override fun visitRange(range: KtExpression, context: Lazy<BindingContext>, type: RangeKtExpressionType, holder: ProblemsHolder) {
        when (type) {
            UNTIL, RANGE_UNTIL, DOWN_TO -> return
            RANGE_TO -> Unit
        }
        val useRangeUntil = range.isPossibleToUseRangeUntil(context)
        if (useRangeUntil xor (this is ReplaceRangeToWithRangeUntilInspection)) return
        if (!isApplicable(range, context, useRangeUntil)) return
        val desc =
            if (useRangeUntil) KotlinBundle.message("inspection.replace.range.to.with.rangeUntil.display.name")
            else KotlinBundle.message("inspection.replace.range.to.with.until.display.name")
        holder.registerProblem(range, desc, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, ReplaceWithUntilQuickFix(useRangeUntil))
    }

    private class ReplaceWithUntilQuickFix(private val useRangeUntil: Boolean) : LocalQuickFix {
        override fun getName(): String =
            if (useRangeUntil) KotlinBundle.message("replace.with.rangeUntil.quick.fix.text")
            else KotlinBundle.message("replace.with.until.quick.fix.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as KtExpression
            applyFix(element, useRangeUntil)
        }
    }

    companion object {
        fun applyFixIfApplicable(expression: KtExpression) {
            val context = lazy { expression.analyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL) }
            val useRangeUntil = expression.isPossibleToUseRangeUntil(context)
            if (isApplicable(expression, context, useRangeUntil)) {
                applyFix(expression, useRangeUntil)
            }
        }

        private fun isApplicable(expression: KtExpression, context: Lazy<BindingContext>, useRangeUntil: Boolean): Boolean {
            val (left, right) = expression.getArguments() ?: return false
            // `until` isn't available for floating point numbers
            fun KtExpression.isRangeUntilOrUntilApplicable() = getType(context.value)
                ?.let { it.isPrimitiveNumberType() && (useRangeUntil || !it.isDouble() && !it.isFloat()) }
            return right?.deparenthesize()?.isMinusOne() == true &&
                    left?.isRangeUntilOrUntilApplicable() == true && right.isRangeUntilOrUntilApplicable() == true
        }

        private fun applyFix(element: KtExpression, useRangeUntil: Boolean) {
            val args = element.getArguments() ?: return
            val operator = if (useRangeUntil) "..<" else " until "
            element.replace(
                KtPsiFactory(element.project).createExpressionByPattern(
                    "$0$operator$1",
                    args.first ?: return,
                    (args.second?.deparenthesize() as? KtBinaryExpression)?.left ?: return
                )
            )
        }

        private fun KtExpression.isMinusOne(): Boolean {
            if (this !is KtBinaryExpression) return false
            if (operationToken != KtTokens.MINUS) return false

            val constantValue = right?.constantValueOrNull()
            val rightValue = (constantValue?.value as? Number)?.toInt() ?: return false
            return rightValue == 1
        }
    }
}

/**
 * Tests: [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.ReplaceRangeToWithUntil]
 */
class ReplaceRangeToWithUntilInspection : AbstractReplaceRangeToWithRangeUntilInspection()

/**
 * Tests: [org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated.ReplaceRangeToWithRangeUntil]
 */
class ReplaceRangeToWithRangeUntilInspection : AbstractReplaceRangeToWithRangeUntilInspection()

private fun KtExpression.deparenthesize() = KtPsiUtil.safeDeparenthesize(this)
