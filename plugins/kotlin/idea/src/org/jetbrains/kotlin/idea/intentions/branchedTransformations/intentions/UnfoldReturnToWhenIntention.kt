// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.types.typeUtil.isNothing

class UnfoldReturnToWhenIntention : LowPriorityAction, SelfTargetingRangeIntention<KtReturnExpression>(
    KtReturnExpression::class.java, KotlinBundle.lazyMessage("replace.return.with.when.expression")
) {
    override fun applicabilityRange(element: KtReturnExpression): TextRange? {
        val whenExpr = element.returnedExpression as? KtWhenExpression ?: return null
        if (!KtPsiUtil.checkWhenExpressionHasSingleElse(whenExpr)) return null
        if (whenExpr.entries.any { it.expression == null }) return null
        return TextRange(element.startOffset, whenExpr.whenKeyword.endOffset)
    }

    override fun applyTo(element: KtReturnExpression, editor: Editor?) {
        val psiFactory = KtPsiFactory(element.project)
        val context = element.analyze()

        val whenExpression = element.returnedExpression as KtWhenExpression
        val newWhenExpression = whenExpression.copied()
        val labelName = element.getLabelName()
        whenExpression.entries.zip(newWhenExpression.entries).forEach { (entry, newEntry) ->
            val expr = entry.expression!!.lastBlockStatementOrThis()
            val newExpr = newEntry.expression!!.lastBlockStatementOrThis()
            newExpr.replace(createReturnExpression(expr, labelName, psiFactory, context))
        }

        element.replace(newWhenExpression)
    }

    private fun createReturnExpression(
        expr: KtExpression,
        labelName: String?,
        psiFactory: KtPsiFactory,
        context: BindingContext
    ): KtExpression {
        val label = labelName?.let { "@$it" } ?: ""
        val returnText = when (expr) {
            is KtBreakExpression, is KtContinueExpression, is KtReturnExpression, is KtThrowExpression -> ""
            else -> if (expr.getResolvedCall(context)?.resultingDescriptor?.returnType?.isNothing() == true) "" else "return$label "
        }

        return psiFactory.createExpressionByPattern("$returnText$0", expr)
    }
}
