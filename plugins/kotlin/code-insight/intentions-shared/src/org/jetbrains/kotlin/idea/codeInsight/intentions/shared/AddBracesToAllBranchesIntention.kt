// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.AddBracesToAllBranchesIntention.Util.allBranchExpressions
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.AddBracesUtils
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.util.match

internal class AddBracesToAllBranchesIntention : KotlinPsiUpdateModCommandAction.Simple<KtExpression>(KtExpression::class) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.braces.to.all.branches")

    override fun isElementApplicable(element: KtExpression, context: ActionContext): Boolean {
        val targetIfOrWhenExpression = Util.targetIfOrWhenExpression(element) ?: return false

        val targetBranchExpressions = targetIfOrWhenExpression.targetBranchExpressions()
        if (targetBranchExpressions.isEmpty()) return false
        if (caretIsOnSingleTargetBranch(targetIfOrWhenExpression, targetBranchExpressions, context.offset)) return false

        return targetIfOrWhenExpression is KtIfExpression || targetIfOrWhenExpression is KtWhenExpression
    }

    override fun getPresentation(context: ActionContext, element: KtExpression): Presentation? {
        val targetIfOrWhenExpression = Util.targetIfOrWhenExpression(element) ?: return null

        val targetBranchExpressions = targetIfOrWhenExpression.targetBranchExpressions()
        if (targetBranchExpressions.isEmpty()) return null
        if (caretIsOnSingleTargetBranch(targetIfOrWhenExpression, targetBranchExpressions, context.offset)) return null

        return when (targetIfOrWhenExpression) {
            is KtIfExpression -> Presentation.of(KotlinBundle.message("add.braces.to.if.all.statements"))
            is KtWhenExpression -> Presentation.of(KotlinBundle.message("add.braces.to.when.all.entries"))
            else -> null
        }
    }

    override fun invoke(actionContext: ActionContext, element: KtExpression, elementContext: Unit, updater: ModPsiUpdater) {
        val targetIfOrWhenExpression = Util.targetIfOrWhenExpression(element) ?: return
        val branches = targetIfOrWhenExpression.targetBranchExpressions()
        for (branch in branches) {
            val container = branch.parent as? KtWhenEntry ?: targetIfOrWhenExpression
            AddBracesUtils.addBraces(container, branch)
        }
    }

    private fun KtExpression.targetBranchExpressions(): List<KtExpression> {
        val branchExpressions = allBranchExpressions()
        if (branchExpressions.size <= 1) return emptyList()
        return branchExpressions.filter { it !is KtBlockExpression }
    }

    private fun caretIsOnSingleTargetBranch(
        targetIfOrWhenExpression: KtExpression,
        targetBranchExpressions: List<KtExpression>,
        caretOffset: Int,
    ): Boolean {
        val singleBranchExpression = targetBranchExpressions.singleOrNull() ?: return false
        val startOffset = when (targetIfOrWhenExpression) {
            is KtIfExpression -> singleBranchExpression.prevIfOrElseKeyword()
            is KtWhenExpression -> singleBranchExpression.getStrictParentOfType<KtWhenEntry>()
            else -> null
        }?.startOffset ?: return false
        return caretOffset in startOffset..singleBranchExpression.endOffset
    }

    private fun KtExpression.prevIfOrElseKeyword(): PsiElement? {
        return getStrictParentOfType<KtContainerNodeForControlStructureBody>()
            ?.siblings(forward = false)
            ?.firstOrNull {
                val elementType = it.elementType
                elementType == KtTokens.IF_KEYWORD || elementType == KtTokens.ELSE_KEYWORD
            }
    }

    object Util {
        fun targetIfOrWhenExpression(element: KtExpression): KtExpression? = when (element) {
            is KtIfExpression -> generateSequence(element) { target ->
                target.parents.match(KtContainerNodeForControlStructureBody::class, last = KtIfExpression::class)
                    ?.takeIf { it.`else` == target }
            }.last()
            is KtWhenExpression -> element
            is KtBlockExpression -> element.parent.let { it as? KtContainerNodeForControlStructureBody ?: it as? KtWhenEntry }
                ?.let { it.parent as? KtExpression }
                ?.let(Util::targetIfOrWhenExpression)
            else -> null
        }

        fun KtExpression.allBranchExpressions(): List<KtExpression> = when (this) {
            is KtIfExpression -> {
                val branchExpressions = mutableListOf<KtExpression>()
                fun collect(ifExpression: KtIfExpression) {
                    branchExpressions.addIfNotNull(ifExpression.then)
                    when (val elseExpression = ifExpression.`else`) {
                        is KtIfExpression -> collect(elseExpression)
                        else -> branchExpressions.addIfNotNull(elseExpression)
                    }
                }
                collect(this)
                branchExpressions
            }
            is KtWhenExpression -> entries.mapNotNull { it.expression }
            else -> emptyList()
        }
    }
}
