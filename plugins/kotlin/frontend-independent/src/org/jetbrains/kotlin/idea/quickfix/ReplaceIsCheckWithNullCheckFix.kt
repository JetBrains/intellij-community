// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange.from
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil.findChildrenOfType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getStartOffsetIn

class ReplaceIsCheckWithNullCheckFix(
    element: KtIsExpression,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtIsExpression>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.is.check.with.null.check")

    override fun invoke(
        context: ActionContext,
        element: KtIsExpression,
        updater: ModPsiUpdater,
    ) {
        val operator = if (element.isNegated) "!=" else "=="
        val typeReference = element.typeReference ?: return
        val commentSaver = CommentSaver(element)
        val expressionStart = element.textRange.startOffset
        val triviaBeforeOperatorStart = element.leftHandSide.textRange.endOffset - expressionStart
        val triviaBeforeOperatorEnd = element.operationReference.textRange.startOffset - expressionStart
        val triviaBeforeTypeStart = element.operationReference.textRange.endOffset - expressionStart
        val triviaBeforeTypeEnd = typeReference.textRange.startOffset - expressionStart
        val triviaBeforeOperator = element.text.substring(triviaBeforeOperatorStart, triviaBeforeOperatorEnd)
        val triviaBeforeType = element.text.substring(triviaBeforeTypeStart, triviaBeforeTypeEnd)
        val newExpression = KtPsiFactory(context.project)
            .createExpressionByPattern("$0${triviaBeforeOperator}$operator${triviaBeforeType}null", element.leftHandSide)
        val newComments = findChildrenOfType(newExpression, PsiComment::class.java).toList()
        val originalComments = findChildrenOfType(element, PsiComment::class.java).toList()
        val newIterator = newComments.iterator()
        val originalIterator = originalComments.iterator()
        while (newIterator.hasNext() && originalIterator.hasNext()) {
            val newComment = newIterator.next()
            val originalComment = originalIterator.next()
            if (newComment.text != originalComment.text) continue
            val rangeInOriginal = from(originalComment.getStartOffsetIn(element), originalComment.textLength)
            commentSaver.elementCreatedByText(newComment, element, rangeInOriginal)
        }
        val result = element.replace(newExpression)
        commentSaver.restore(result)
    }

}
