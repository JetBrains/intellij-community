// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.getLineNumber
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

class AddBracesIntention : SelfTargetingIntention<KtElement>(KtElement::class.java, KotlinBundle.lazyMessage("add.braces")) {
    override fun isApplicableTo(element: KtElement, caretOffset: Int): Boolean {
        val expression = element.getTargetExpression(caretOffset) ?: return false
        if (expression is KtBlockExpression) return false

        return when (val parent = expression.parent) {
            is KtContainerNode -> {
                val description = parent.description() ?: return false
                setTextGetter(KotlinBundle.lazyMessage("add.braces.to.0.statement", description))
                true
            }
            is KtWhenEntry -> {
                setTextGetter(KotlinBundle.lazyMessage("add.braces.to.when.entry"))
                true
            }
            else -> {
                false
            }
        }
    }

    override fun applyTo(element: KtElement, editor: Editor?) {
        if (editor == null) throw IllegalArgumentException("This intention requires an editor")
        val expression = element.getTargetExpression(editor.caretModel.offset) ?: return
        addBraces(element, expression)
    }

    private fun KtElement.getTargetExpression(caretLocation: Int): KtExpression? {
        return when (this) {
            is KtIfExpression -> {
                val thenExpr = then ?: return null
                val elseExpr = `else`
                if (elseExpr != null && caretLocation >= (elseKeyword?.startOffset ?: return null)) {
                    elseExpr
                } else {
                    thenExpr
                }
            }

            is KtLoopExpression -> body
            is KtWhenEntry -> expression
            else -> null
        }
    }

    companion object {
        fun addBraces(element: KtElement, expression: KtExpression) {
            val psiFactory = KtPsiFactory(element)

            val semicolon = element.getNextSiblingIgnoringWhitespaceAndComments()?.takeIf { it.node.elementType == KtTokens.SEMICOLON }
            if (semicolon != null) {
                val afterSemicolon = semicolon.getNextSiblingIgnoringWhitespace()
                if (semicolon.getLineNumber() == afterSemicolon?.getLineNumber())
                    semicolon.replace(psiFactory.createNewLine())
                else
                    semicolon.delete()
            }

            val nextComment = when {
                element is KtDoWhileExpression -> null // bound to the closing while
                element is KtIfExpression && expression === element.then && element.`else` != null -> null // bound to else
                else -> {
                    val nextSibling = element.getNextSiblingIgnoringWhitespace() ?: element.parent.getNextSiblingIgnoringWhitespace()
                    nextSibling.takeIf { it is PsiComment && it.getLineNumber() == element.getLineNumber(start = false) }
                }
            }
            nextComment?.delete()

            val allChildren = element.allChildren
            val (first, last) = when (element) {
                is KtIfExpression -> element.rightParenthesis to allChildren.last
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
            when (element) {
                is KtDoWhileExpression ->
                    // remove new line between '}' and while
                    (element.body?.parent?.nextSibling as? PsiWhiteSpace)?.delete()
                is KtIfExpression ->
                    (result?.parent?.nextSibling as? PsiWhiteSpace)?.delete()
            }
            saver?.restore(result, forceAdjustIndent = false)
        }
    }
}
