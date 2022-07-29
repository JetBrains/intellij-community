// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.intentions.getArguments
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isDouble
import org.jetbrains.kotlin.types.typeUtil.isFloat
import org.jetbrains.kotlin.types.typeUtil.isPrimitiveNumberType

class ReplaceRangeToWithUntilInspection : AbstractRangeInspection() {
    override fun visitRangeTo(expression: KtExpression, context: BindingContext, holder: ProblemsHolder) {
        if (!isApplicable(expression, context)) return
        holder.registerProblem(
            expression,
            KotlinBundle.message("inspection.replace.range.to.with.until.display.name"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            ReplaceWithUntilQuickFix()
        )
    }

    override fun visitUntil(expression: KtExpression, context: BindingContext, holder: ProblemsHolder) {
    }

    override fun visitDownTo(expression: KtExpression, context: BindingContext, holder: ProblemsHolder) {
    }

    class ReplaceWithUntilQuickFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("replace.with.until.quick.fix.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as KtExpression
            applyFix(element)
        }
    }

    companion object {
        fun applyFixIfApplicable(expression: KtExpression) {
            if (isApplicable(expression, expression.analyze(BodyResolveMode.PARTIAL_NO_ADDITIONAL))) applyFix(expression)
        }

        private fun isApplicable(expression: KtExpression, context: BindingContext): Boolean {
            val (left, right) = expression.getArguments() ?: return false
            // `until` isn't available for floating point numbers
            fun KtExpression.isIntegerType() = context.getType(this)
                ?.let { it.isPrimitiveNumberType() && !it.isDouble() && !it.isFloat() }
            return right?.deparenthesize()?.isMinusOne() == true && left?.isIntegerType() == true && right.isIntegerType() == true
        }

        private fun applyFix(element: KtExpression) {
            val args = element.getArguments() ?: return
            element.replace(
                KtPsiFactory(element).createExpressionByPattern(
                    "$0 until $1",
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

private fun KtExpression.deparenthesize() = KtPsiUtil.safeDeparenthesize(this)
