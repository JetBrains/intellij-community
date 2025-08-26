// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.AddBracesToAllBranchesIntention.Util.allBranchExpressions
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.AddBracesToAllBranchesIntention.Util.targetIfOrWhenExpression
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class RemoveBracesFromAllBranchesIntention  : KotlinPsiUpdateModCommandAction.Simple<KtExpression>(KtExpression::class) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("remove.braces.from.all.branches")

    override fun isElementApplicable(element: KtExpression, context: ActionContext): Boolean {
        val targetIfOrWhenExpression = targetIfOrWhenExpression(element) ?: return false

        val targetBranchExpressions = targetIfOrWhenExpression.targetBranchExpressions()
        if (targetBranchExpressions.isEmpty() || targetBranchExpressions.any { !RemoveBracesIntention.Holder.isApplicableTo(it) }) return false
        if (caretIsOnSingleTargetBranch(targetIfOrWhenExpression, targetBranchExpressions, context.offset)) return false

        return targetIfOrWhenExpression is KtIfExpression || targetIfOrWhenExpression is KtWhenExpression
    }

    override fun getPresentation(context: ActionContext, element: KtExpression): Presentation? {
        val targetIfOrWhenExpression = targetIfOrWhenExpression(element) ?: return null

        val targetBranchExpressions = targetIfOrWhenExpression.targetBranchExpressions()
        if (targetBranchExpressions.isEmpty() || targetBranchExpressions.any { !RemoveBracesIntention.Holder.isApplicableTo(it) }) return null
        if (caretIsOnSingleTargetBranch(targetIfOrWhenExpression, targetBranchExpressions, context.offset)) return null

        return when (targetIfOrWhenExpression) {
            is KtIfExpression -> Presentation.of(KotlinBundle.message("remove.braces.from.if.all.statements"))
            is KtWhenExpression -> Presentation.of(KotlinBundle.message("remove.braces.from.when.all.entries"))
            else -> null
        }
    }

    override fun invoke(actionContext: ActionContext, element: KtExpression, elementContext: Unit, updater: ModPsiUpdater) {
        val targetIfOrWhenExpression = targetIfOrWhenExpression(element) ?: return
        targetIfOrWhenExpression.targetBranchExpressions().forEach {
            RemoveBracesIntention.Holder.removeBraces(actionContext, targetIfOrWhenExpression, it, updater)
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