// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.MergeIfsIntention.Holder.nestedIf
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class MergeIfsIntention : SelfTargetingIntention<KtExpression>(KtExpression::class.java, KotlinBundle.lazyMessage("merge.if.s")) {
    override fun isApplicableTo(element: KtExpression, caretOffset: Int): Boolean =
        element.ifExpression()?.isApplicable(caretOffset) == true

    override fun applyTo(element: KtExpression, editor: Editor?) {
        element.ifExpression()?.let(Holder::applyTo)
    }

    private fun KtExpression.ifExpression(): KtIfExpression? = when (this) {
        is KtIfExpression -> this
        is KtBlockExpression -> parent.parent as? KtIfExpression
        else -> null
    }

    private fun KtIfExpression.isApplicable(caretOffset: Int): Boolean {
        if (`else` != null) return false
        val then = then ?: return false

        val nestedIf = then.nestedIf() ?: return false
        if (nestedIf.`else` != null) return false

        return caretOffset !in TextRange(nestedIf.startOffset, nestedIf.endOffset + 1)
    }

    object Holder {
        fun applyTo(element: KtIfExpression): Int {
            val then = element.then
            val nestedIf = then?.nestedIf() ?: return -1
            val condition = element.condition ?: return -1
            val secondCondition = nestedIf.condition ?: return -1
            val nestedBody = nestedIf.then ?: return -1

            val psiFactory = KtPsiFactory(element.project)

            val comments = element.allChildren.filter { it is PsiComment }.toList() + then.safeAs<KtBlockExpression>()
                ?.allChildren
                ?.filter { it is PsiComment }
                ?.toList()
                .orEmpty()

            if (comments.isNotEmpty()) {
                val parent = element.parent
                comments.forEach { comment ->
                    parent.addBefore(comment, element)
                    parent.addBefore(psiFactory.createNewLine(), element)
                    comment.delete()
                }

                element.findExistingEditor()?.caretModel?.moveToOffset(element.startOffset)
            }

            condition.replace(psiFactory.createExpressionByPattern("$0 && $1", condition, secondCondition))
            return then.replace(nestedBody).reformatted(true).textRange.startOffset
        }

        internal fun KtExpression.nestedIf(): KtIfExpression? = when (this) {
            is KtBlockExpression -> this.statements.singleOrNull() as? KtIfExpression
            is KtIfExpression -> this
            else -> null
        }
    }
}