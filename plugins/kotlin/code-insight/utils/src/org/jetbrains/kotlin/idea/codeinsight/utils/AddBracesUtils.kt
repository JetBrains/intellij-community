// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

object AddBracesUtils {
    fun addBraces(element: KtElement, expression: KtExpression) {
        val psiFactory = KtPsiFactory(element.project)

        val semicolon = element.getNextSiblingIgnoringWhitespaceAndComments()?.takeIf { it.node.elementType == KtTokens.SEMICOLON }
        if (semicolon != null) {
            val afterSemicolon = semicolon.getNextSiblingIgnoringWhitespace()
            if (semicolon.getLineNumber() == afterSemicolon?.getLineNumber()) {
                semicolon.replace(psiFactory.createNewLine())
            } else {
                semicolon.delete()
            }
        }

        val nextComment = when (element) {
            is KtDoWhileExpression -> null // bound to the closing while
            is KtIfExpression if expression === element.then && element.`else` != null -> null // bound to else
            else -> {
                val nextSibling = element.getNextSiblingIgnoringWhitespace() ?: element.parent.getNextSiblingIgnoringWhitespace()
                nextSibling.takeIf { it is PsiComment && it.getLineNumber() == element.getLineNumber(start = false) }
            }
        }
        nextComment?.delete()

        val allChildren = element.allChildren
        val (first, last) = when (element) {
            is KtIfExpression -> {
                val containerNode = expression.parent
                val first = containerNode.getPrevSiblingIgnoringWhitespaceAndComments()
                val last = containerNode.siblings(withItself = false)
                    .takeWhile { it is PsiWhiteSpace || it is PsiComment }.lastOrNull() ?: containerNode
                first to last
            }

            is KtForExpression -> element.rightParenthesis to allChildren.last
            is KtWhileExpression -> element.rightParenthesis to allChildren.last
            is KtWhenEntry -> element.arrow to allChildren.last
            is KtDoWhileExpression -> allChildren.first to element.whileKeyword
            else -> null to null
        }

        val saver = if (first != null && last != null) {
            val range = PsiChildRange(first, last)
            CommentSaver(range).also {
                range.filterIsInstance<PsiComment>().toList().forEach { it.delete() }
            }
        } else {
            null
        }

        val result = expression.replace(psiFactory.createSingleStatementBlock(expression, nextComment = nextComment?.text))

        val parent = when (element) {
            is KtDoWhileExpression -> element.body?.parent
            is KtIfExpression -> result.parent
            is KtWhenEntry -> result
            else -> null
        }
        parent?.let {
            // remove new line between '{' and if/when
            (it.prevSibling as? PsiWhiteSpace)?.delete()
            // remove new line between '}' and while
            (it.nextSibling as? PsiWhiteSpace)?.delete()
        }
        saver?.restore(result, forceAdjustIndent = false)
    }
}
