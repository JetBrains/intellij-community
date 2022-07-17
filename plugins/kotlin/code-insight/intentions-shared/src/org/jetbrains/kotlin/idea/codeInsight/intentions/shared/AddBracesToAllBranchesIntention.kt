// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.*

internal class AddBracesToAllBranchesIntention : SelfTargetingIntention<KtExpression>(
    KtExpression::class.java,
    KotlinBundle.lazyMessage("add.braces.to.all.branches")
) {
    override fun isApplicableTo(element: KtExpression, caretOffset: Int): Boolean {
        val targetIfOrWhenExpression = targetIfOrWhenExpression(element) ?: return false
        if (targetIfOrWhenExpression.targetBranchExpressions().isEmpty()) return false
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
