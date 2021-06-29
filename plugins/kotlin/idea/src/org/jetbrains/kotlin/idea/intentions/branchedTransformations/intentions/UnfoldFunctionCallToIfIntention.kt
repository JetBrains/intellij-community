// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.intentions.branches
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.util.reformatted
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

class UnfoldFunctionCallToIfIntention : SelfTargetingIntention<KtCallExpression>(
    KtCallExpression::class.java,
    KotlinBundle.lazyMessage("replace.function.call.with.if"),
) {
    override fun isApplicableTo(element: KtCallExpression, caretOffset: Int): Boolean {
        if (element.calleeExpression == null) return false
        val (ifExpression, _) = element.ifExpressionArgument() ?: return false
        val branches = ifExpression.branches
        if (branches.size < 2) return false
        return branches.all { it.singleExpression() != null }
    }

    override fun applyTo(element: KtCallExpression, editor: Editor?) {
        val (ifExpression, argumentIndex) = element.ifExpressionArgument() ?: return
        val qualifiedOrCall = element.getQualifiedExpressionForSelectorOrThis()
        ifExpression.branches.forEach {
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
        qualifiedOrCall.replace(ifExpression).reformatted()
    }

    private fun KtCallExpression.ifExpressionArgument(): Pair<KtIfExpression, Int>? =
        valueArguments.mapIndexedNotNull { index, argument ->
            val expression = argument.getArgumentExpression() as? KtIfExpression ?: return@mapIndexedNotNull null
            expression to index
        }.singleOrNull()

    private fun KtExpression?.singleExpression(): KtExpression? =
        if (this is KtBlockExpression) this.statements.singleOrNull() else this
}