// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.getControlFlowElementDescription
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class RemoveBracesIntention: KotlinApplicableModCommandAction<KtElement, Unit>(KtElement::class) {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("remove.braces")

    override fun isApplicableByPsi(element: KtElement): Boolean {
        val block = element.findChildBlock() ?: return false
        if (!Holder.isApplicableTo(block)) return false
        return when(block.parent) {
            is KtContainerNode, is KtWhenEntry -> true
            else -> false
        }
    }

    override fun getPresentation(context: ActionContext, element: KtElement): Presentation? {
        val block = element.findChildBlock() ?: return null
        if (!Holder.isApplicableTo(block)) return null

        return when (val container = block.parent) {
            is KtContainerNode -> {
                val description = container.getControlFlowElementDescription() ?: return null
                Presentation.of(KotlinBundle.message("remove.braces.from.0.statement", description))
            }
            is KtWhenEntry -> {
                Presentation.of(KotlinBundle.message("remove.braces.from.when.entry"))
            }

            else -> null
        }
    }

    override fun invoke(actionContext: ActionContext, element: KtElement, elementContext: Unit, updater: ModPsiUpdater) {
        val block = element.findChildBlock() ?: return
        Holder.removeBraces(actionContext, element, block, updater)
    }

    override fun stopSearchAt(element: PsiElement, context: ActionContext): Boolean {
        return element is KtBlockExpression && element.parent !is KtWhenEntry
    }

    private fun KtElement.findChildBlock(): KtBlockExpression? = when (this) {
        is KtBlockExpression -> this
        is KtLoopExpression -> body as? KtBlockExpression
        is KtWhenEntry -> expression as? KtBlockExpression
        else -> null
    }

    object Holder {
        fun isApplicableTo(block: KtBlockExpression): Boolean {
            val singleStatement = block.statements.singleOrNull() ?: return false
            if (singleStatement is KtLambdaExpression && singleStatement.functionLiteral.arrow == null) return false
            when (val container = block.parent) {
                is KtContainerNode -> {
                    if (singleStatement is KtProperty || singleStatement is KtClass) return false
                    if (singleStatement is KtIfExpression) {
                        val elseExpression = (container.parent as? KtIfExpression)?.`else`
                        if (elseExpression != null && elseExpression != block) return false
                    }
                    return true
                }
                is KtWhenEntry -> {
                    return singleStatement !is KtNamedDeclaration
                }
                else -> return false
            }
        }

        fun removeBraces(actionContext: ActionContext, element: KtElement, block: KtBlockExpression, updater: ModPsiUpdater) {
            val project = element.project
            val factory = KtPsiFactory(project)
            val statement = block.statements.single()
            val caretOnAfterStatement = updater.caretOffset >= statement.endOffset

            val container = block.parent
            val construct = container.parent as KtExpression
            statement.handleComments(block)

            val newElement = block.replace(statement.copy())
            updater.moveCaretTo(if (caretOnAfterStatement) newElement.endOffset else newElement.startOffset)

            if (construct is KtDoWhileExpression) {
                newElement.parent!!.addAfter(factory.createNewLine(), newElement)
            } else {
                val document = actionContext.file.fileDocument
                val rightMargin = CodeStyle.getSettings(project).getRightMargin(element.language)
                val line = document.getLineNumber(newElement.startOffset)
                val lineStartOffset = document.getLineStartOffset(line)
                val lineEndOffset = document.getLineEndOffset(line) + newElement.textLength
                if (lineEndOffset - lineStartOffset >= rightMargin) {
                    newElement.parent.addBefore(factory.createNewLine(), newElement)
                }
            }

            if (construct is KtIfExpression &&
                container.node.elementType == KtNodeTypes.ELSE &&
                construct.parent is KtExpression &&
                construct.parent !is KtStatementExpression
            ) {
                val replaced = construct.replace(factory.createExpressionByPattern("($0)", construct))
                (replaced.children[0] as? KtIfExpression)?.`else`?.let {
                    updater.moveCaretTo(if (caretOnAfterStatement) it.endOffset else it.startOffset)
                }
            }
        }

        private fun KtExpression.handleComments(block: KtBlockExpression) {
            val nextComments = comments(forward = true)
            val prevComments = comments(forward = false).reversed()
            val blockParent = block.parent
            if (prevComments.isNotEmpty()) {
                blockParent.addRangeBefore(prevComments.first(), prevComments.last(), block)
            }
            if (nextComments.isNotEmpty()) {
                blockParent.addRangeAfter(nextComments.first(), nextComments.last(), block)
            }
        }

        private fun KtExpression.comments(forward: Boolean): List<PsiElement> {
            val elements = siblings(forward = forward, withItself = false)
                .takeWhile { it is PsiComment || it is PsiWhiteSpace }
                .toList()
            return if (elements.any { it is PsiComment }) elements else emptyList()
        }

    }

    override fun KaSession.prepareContext(element: KtElement) {}
}
