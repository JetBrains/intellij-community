// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class MergeElseIfIntention : SelfTargetingIntention<KtIfExpression>(
  KtIfExpression::class.java,
  KotlinBundle.lazyMessage("merge.else.if")
) {
    override fun isApplicableTo(element: KtIfExpression, caretOffset: Int): Boolean {
        val elseBody = element.`else` ?: return false
        val nestedIf = elseBody.nestedIf() ?: return false
        return nestedIf.`else` == null
    }

    override fun applyTo(element: KtIfExpression, editor: Editor?) {
        val nestedIf = element.`else`?.nestedIf() ?: return
        val condition = nestedIf.condition ?: return
        val nestedBody = nestedIf.then ?: return

        val psiFactory = KtPsiFactory(element.project)
        element.`else`?.replace(psiFactory.createExpressionByPattern("if ($0) $1", condition, nestedBody))
    }
}

private fun KtExpression.nestedIf(): KtIfExpression? =
    if (this is KtBlockExpression) {
        this.statements.singleOrNull() as? KtIfExpression
    } else {
        null
    }