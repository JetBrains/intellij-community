// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.*


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

    companion object {
        private fun KtExpression.nestedIf() =
            if (this is KtBlockExpression) {
                this.statements.singleOrNull() as? KtIfExpression
            } else {
                null
            }
    }
}