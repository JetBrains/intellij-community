// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.AddBracesToAllBranchesIntention.Companion.allBranchExpressions
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.AddBracesToAllBranchesIntention.Companion.targetIfOrWhenExpression
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class RemoveBracesFromAllBranchesIntention : SelfTargetingIntention<KtExpression>(
    KtExpression::class.java,
    KotlinBundle.lazyMessage("remove.braces.from.all.branches")
) {
    override fun isApplicableTo(element: KtExpression, caretOffset: Int): Boolean {
        val targetIfOrWhenExpression = targetIfOrWhenExpression(element) ?: return false

        val targetBranchExpressions = targetIfOrWhenExpression.targetBranchExpressions()
        if (targetBranchExpressions.isEmpty() || targetBranchExpressions.any { !RemoveBracesIntention.isApplicableTo(it) }) return false
        if (caretIsOnSingleTargetBranch(targetIfOrWhenExpression, targetBranchExpressions, caretOffset)) return false

        when (targetIfOrWhenExpression) {
            is KtIfExpression -> setTextGetter(KotlinBundle.lazyMessage("remove.braces.from.if.all.statements"))
            is KtWhenExpression -> setTextGetter(KotlinBundle.lazyMessage("remove.braces.from.when.all.entries"))
        }
        return true
    }

    override fun applyTo(element: KtExpression, editor: Editor?) {
        val targetIfOrWhenExpression = targetIfOrWhenExpression(element) ?: return
        targetIfOrWhenExpression.targetBranchExpressions().forEach {
            RemoveBracesIntention.removeBraces(targetIfOrWhenExpression, it)
        }
    }

    private fun KtExpression.targetBranchExpressions(): List<KtBlockExpression> {
        val branchExpressions = allBranchExpressions()
        if (branchExpressions.size <= 1) return emptyList()
        return branchExpressions.filterIsInstance<KtBlockExpression>()
    }

    private fun caretIsOnSingleTargetBranch(
        targetIfOrWhenExpression: KtExpression,
        targetBranchExpressions: List<KtBlockExpression>,
        caretOffset: Int,
    ): Boolean {
        val singleBranchExpression = targetBranchExpressions.singleOrNull() ?: return false
        val startOffset = when (targetIfOrWhenExpression) {
            is KtIfExpression -> singleBranchExpression
            is KtWhenExpression -> singleBranchExpression.getStrictParentOfType<KtWhenEntry>()
            else -> null
        }?.startOffset ?: return false
        return caretOffset in startOffset..singleBranchExpression.endOffset
    }
}