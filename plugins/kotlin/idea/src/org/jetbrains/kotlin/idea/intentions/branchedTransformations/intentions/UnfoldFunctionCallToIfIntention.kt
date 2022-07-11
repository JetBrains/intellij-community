// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

class UnfoldFunctionCallToIfIntention : SelfTargetingRangeIntention<KtCallExpression>(
    KtCallExpression::class.java,
    KotlinBundle.lazyMessage("replace.function.call.with.if"),
) {
    override fun applicabilityRange(element: KtCallExpression): TextRange? =
        if (canUnFoldToIf<KtIfExpression>(element)) element.calleeExpression?.textRange else null

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        unFoldToIf<KtIfExpression>(element)
    }

    companion object {
        private inline fun <reified T: KtExpression> canUnFoldToIf(element: KtCallExpression): Boolean {
            if (element.calleeExpression == null) return false
            val (argument, _) = element.argumentOfType<T>() ?: return false
            val branches = FoldIfToFunctionCallIntention.branches(argument) ?: return false
            return branches.all { it.singleExpression() != null }
        }

        private inline fun <reified T: KtExpression> unFoldToIf(element: KtCallExpression) {
            val (argument, argumentIndex) = element.argumentOfType<T>() ?: return
            val branches = FoldIfToFunctionCallIntention.branches(argument) ?: return
            val qualifiedOrCall = element.getQualifiedExpressionForSelectorOrThis()
            branches.forEach {
                val branchExpression = it.singleExpression() ?: return@forEach
                val copied = qualifiedOrCall.copy()
                val valueArguments = when (copied) {
                    is KtCallExpression -> copied.valueArguments
                    is KtQualifiedExpression -> copied.callExpression?.valueArguments
                    else -> null
                } ?: return@forEach
                valueArguments[argumentIndex]?.getArgumentExpression()?.replace(branchExpression)
                branchExpression.replace(copied)
            }
            qualifiedOrCall.replace(argument).reformatted()
        }

        private inline fun <reified T: KtExpression> KtCallExpression.argumentOfType(): Pair<T, Int>? =
            valueArguments.mapIndexedNotNull { index, argument ->
                val expression = argument.getArgumentExpression() as? T ?: return@mapIndexedNotNull null
                expression to index
            }.singleOrNull()

        private fun KtExpression?.singleExpression(): KtExpression? =
            if (this is KtBlockExpression) this.statements.singleOrNull() else this
    }
}
