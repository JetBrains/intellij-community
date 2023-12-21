// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections

import com.intellij.psi.PsiComment
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object MergeIfsUtils {

    /**
     * Takes an outer "if" expression, finds a **single** nested "if" expression in its "then" branch,
     * and merges it with the outer "if".
     *
     * For example, transforms this:
     * ```kt
     * if (condition1) {
     *     if (condition2) {
     *         println("Hello, Kotlin!")
     *     }
     * }
     * ```
     *
     * to this:
     * ```kt
     * if (condition1 && condition2) {
     *     println("Hello, Kotlin!")
     * }
     * ```
     *
     * The function also prepends commentaries from "then" body to the outer "if".
     *
     * @param element The outer "if" expression to merge nested "if" with.
     * @return The start offset of the replaced "then" block, or -1 if merging fails.
     */
    fun mergeNestedIf(element: KtIfExpression): Int {
        val then = element.then
        val nestedIf = then?.asSingleIfExpression() ?: return -1
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

    fun KtExpression.asSingleIfExpression(): KtIfExpression? = when (this) {
        is KtBlockExpression -> this.statements.singleOrNull() as? KtIfExpression
        is KtIfExpression -> this
        else -> null
    }
}
