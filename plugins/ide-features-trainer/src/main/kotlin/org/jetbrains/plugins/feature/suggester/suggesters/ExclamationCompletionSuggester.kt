package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.psi.*
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.cache.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.cache.UserAnActionsHistory
import org.jetbrains.plugins.feature.suggester.changes.*

class ExclamationCompletionSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "You can also finish code completion with '!' character"
        const val DESCRIPTOR_ID = "editing.completion.finishByDotEtc"
    }

    private var exclaimingExpression: PossibleExclaimingExpression? = null
    private var lastCompletionCall = 0L

    private class PossibleExclaimingExpression(val action: UserAction, val exprText: String, val startOffset: Int)

    override fun getSuggestion(actions: UserActionsHistory, anActions: UserAnActionsHistory): Suggestion {
        updateCompletionStatus()
        when (val lastAction = actions.last()) {
            is ChildAddedAction -> {
                val child = lastAction.newChild ?: return NoSuggestion
                val expr = child.getFramingExpression()
                if (expr != null) {
                    updateStartOffset(expr.startOffset, expr.endOffset, 0)
                    updateExclaimingExpression(expr, lastAction)
                } else {
                    updateStartOffset(child.startOffset, child.endOffset, 0)
                }
            }
            is ChildReplacedAction -> {
                val newChild = lastAction.newChild ?: return NoSuggestion
                val oldChild = lastAction.oldChild ?: return NoSuggestion
                if (newChild is PsiPrefixExpression && oldChild is PsiExpression) {
                    updateStartOffset(newChild.startOffset, newChild.endOffset, oldChild.textLength)
                    if (isExpressionExclaimed(newChild, oldChild, actions)) {
                        exclaimingExpression = null
                        return createSuggestion(DESCRIPTOR_ID, POPUP_MESSAGE)
                    }
                }
                val expr = newChild.getFramingExpression()
                if (expr != null) {
                    updateStartOffset(expr.startOffset, expr.endOffset, oldChild.textLength)
                    updateExclaimingExpression(expr, lastAction)
                } else {
                    updateStartOffset(newChild.startOffset, newChild.endOffset, oldChild.textLength)
                }
            }
            is ChildRemovedAction -> {
                val (parent, child) = lastAction
                if (parent == null || child == null) return NoSuggestion
                if (exclaimingExpression != null) {
                    when {
                        parent.startOffset >= exclaimingExpression!!.startOffset -> {
                            // do nothing
                        }
                        parent.endOffset <= exclaimingExpression!!.startOffset -> {
                            exclaimingExpression = PossibleExclaimingExpression(
                                exclaimingExpression!!.action,
                                exclaimingExpression!!.exprText,
                                exclaimingExpression!!.startOffset - child.textLength
                            )
                        }
                        else -> {
                            exclaimingExpression = null   //we can't recalculate offset in this case
                        }
                    }
                }
            }
            is ChildMovedAction -> {
                if (lastAction.parent != null && lastAction.oldParent != null) {
                    //we can't recalculate offset in this case
                    exclaimingExpression = null
                }
            }
        }
        return NoSuggestion
    }

    override fun getId(): String = "Exclamation completion suggester"

    private fun updateCompletionStatus() {
        val phase = CompletionServiceImpl.getCompletionPhase()
        if (phase != null) {
            val indicator = phase.indicator
            if (indicator != null && !indicator.isAutopopupCompletion) {
                lastCompletionCall = System.currentTimeMillis()
            }
        }
    }

    private fun updateStartOffset(startOffset: Int, endOffset: Int, oldLength: Int) {
        if (exclaimingExpression == null) {
            return
        }
        val curStartOffset = exclaimingExpression!!.startOffset
        if (startOffset in (startOffset + 1) until endOffset) {
            exclaimingExpression = null
        } else if (startOffset < curStartOffset && endOffset <= curStartOffset) {
            exclaimingExpression = PossibleExclaimingExpression(
                exclaimingExpression!!.action,
                exclaimingExpression!!.exprText,
                curStartOffset + endOffset - startOffset - oldLength
            )
        }
    }

    private fun updateExclaimingExpression(expr: PsiExpression, action: UserAction) {
        if (expr is PsiPrefixExpression) return
        val exprType = expr.type
        if (exprType != PsiType.BOOLEAN) return
        //let's check that this change is after completion action
        val delta = System.currentTimeMillis() - lastCompletionCall
        if (delta > 50L) return
        exclaimingExpression = PossibleExclaimingExpression(action, expr.text, expr.textRange.startOffset)
    }

    private fun isExpressionExclaimed(
        prefixExpr: PsiPrefixExpression,
        expr: PsiExpression,
        actions: UserActionsHistory
    ): Boolean {
        if (exclaimingExpression == null) {
            return false
        }
        val newExprText = if (expr is PsiMethodCallExpression)
            expr.methodExpression.text + "(" else expr.text
        if (exclaimingExpression!!.exprText.startsWith(newExprText)) return false
        if (prefixExpr.operationSign.text != "!") return false
        if (prefixExpr.startOffset != exclaimingExpression!!.startOffset) return false
        if (!actions.contains(exclaimingExpression!!.action)) {
            //looks like action is too old, let's remove it, possibly not to annoy user
            exclaimingExpression = null
            return false
        }
        return true
    }

    private fun PsiElement.getFramingExpression(): PsiExpression? {
        val initialTextRange = textRange
        var cur: PsiElement? = this
        while (cur != null && cur.textRange == initialTextRange) {
            if (cur is PsiExpression) {
                return cur
            }
            cur = cur.parent
        }
        return null
    }
}