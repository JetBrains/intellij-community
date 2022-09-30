// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class AddBracesToAllBranchesIntention : SelfTargetingIntention<KtExpression>(
    KtExpression::class.java,
    KotlinBundle.lazyMessage("add.braces.to.all.branches")
) {
    override fun isApplicableTo(element: KtExpression, caretOffset: Int): Boolean {
        val targetIfOrWhenExpression = targetIfOrWhenExpression(element) ?: return false

        val targetBranchExpressions = targetIfOrWhenExpression.targetBranchExpressions()
        if (targetBranchExpressions.isEmpty()) return false
        if (caretIsOnSingleTargetBranch(targetIfOrWhenExpression, targetBranchExpressions, caretOffset)) return false

        when (targetIfOrWhenExpression) {
            is KtIfExpression -> setTextGetter(KotlinBundle.lazyMessage("add.braces.to.if.all.statements"))
            is KtWhenExpression -> setTextGetter(KotlinBundle.lazyMessage("add.braces.to.when.all.entries"))
        }
        return true
    }

    override fun applyTo(element: KtExpression, editor: Editor?) {
        val targetIfOrWhenExpression = targetIfOrWhenExpression(element) ?: return
        targetIfOrWhenExpression.targetBranchExpressions().forEach {
            AddBracesIntention.addBraces(targetIfOrWhenExpression, it)
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

    companion object {
        fun targetIfOrWhenExpression(element: KtExpression): KtExpression? {
            return when (element) {
                is KtIfExpression -> {
                    var target = element
                    while (true) {
                        val container = target.parent as? KtContainerNodeForControlStructureBody ?: break
                        val parent = container.parent as? KtIfExpression ?: break
                        if (parent.`else` != target) break
                        target = parent
                    }
                    target
                }
                is KtWhenExpression -> element
                is KtBlockExpression -> (element.parent.parent as? KtExpression)?.let { targetIfOrWhenExpression(it) }
                else -> null
            }
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
