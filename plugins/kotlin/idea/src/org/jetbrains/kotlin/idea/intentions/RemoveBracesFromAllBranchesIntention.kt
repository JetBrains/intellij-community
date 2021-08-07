// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.AddBracesToAllBranchesIntention.Companion.allBranchExpressions
import org.jetbrains.kotlin.idea.intentions.AddBracesToAllBranchesIntention.Companion.targetIfOrWhenExpression
import org.jetbrains.kotlin.psi.*

class RemoveBracesFromAllBranchesIntention : SelfTargetingIntention<KtExpression>(
    KtExpression::class.java,
    KotlinBundle.lazyMessage("remove.braces.from.all.branches")
) {
    override fun isApplicableTo(element: KtExpression, caretOffset: Int): Boolean {
        val targetIfOrWhenExpression = targetIfOrWhenExpression(element) ?: return false
        val targetBranchExpressions = targetIfOrWhenExpression.targetBranchExpressions()
        if (targetBranchExpressions.isEmpty() || targetBranchExpressions.any { !RemoveBracesIntention.isApplicableTo(it) }) return false
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
}