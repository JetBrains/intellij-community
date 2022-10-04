// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.appendSemicolonBeforeLambdaContainingElement
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*

class RemoveUnnecessaryParenthesesIntention : SelfTargetingRangeIntention<KtParenthesizedExpression>(
    KtParenthesizedExpression::class.java, KotlinBundle.lazyMessage("remove.unnecessary.parentheses")
) {
    override fun applicabilityRange(element: KtParenthesizedExpression): TextRange? {
        element.expression ?: return null
        if (!KtPsiUtil.areParenthesesUseless(element)) return null
        return element.textRange
    }

    override fun applyTo(element: KtParenthesizedExpression, editor: Editor?) {
        val commentSaver = CommentSaver(element)
        val innerExpression = element.expression ?: return
        val binaryExpressionParent = element.parent as? KtBinaryExpression
        val replaced = if (binaryExpressionParent != null &&
            innerExpression is KtBinaryExpression &&
            binaryExpressionParent.right == element
        ) {
            binaryExpressionParent.replace(
                KtPsiFactory(element.project).createExpressionByPattern(
                    "$0 $1 $2 $3 $4",
                    binaryExpressionParent.left!!.text,
                    binaryExpressionParent.operationReference.text,
                    innerExpression.left!!.text,
                    innerExpression.operationReference.text,
                    innerExpression.right!!.text,
                )
            )
        } else
            element.replace(innerExpression)

        if (innerExpression.firstChild is KtLambdaExpression) {
            KtPsiFactory(element.project).appendSemicolonBeforeLambdaContainingElement(replaced)
        }

        commentSaver.restore(replaced)
    }
}
