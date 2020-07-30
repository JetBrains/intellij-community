package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.PsiAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory

class SurroundWithSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not to use Surround With action?"
        const val SUGGESTING_ACTION_ID = "SurroundWith"
        const val SUGGESTING_TIP_FILENAME = "neue-SurroundWith.html"
    }

    private class SurroundingStatementData(var surroundingStatement: PsiElement) {
        val startOffset: Int = surroundingStatement.startOffset
        var expectedCloseBraceErrorElement: PsiErrorElement? = null
        var firstStatementInBlockText: String = ""
        var initialStatementsCount: Int = -1

        fun getStatementsFromBlock(): List<PsiElement> {
            val codeBlock = surroundingStatement.children.lastOrNull()?.children?.lastOrNull() ?: return emptyList()
            return if (codeBlock is PsiCodeBlock) {
                val statements = codeBlock.statements.toList()
                if (statements.any { it !is PsiStatement }) {
                    emptyList()
                } else {
                    statements
                }
            } else {
                val statements = codeBlock.children.toList()
                if (statements.any { it !is KtExpression }) {
                    emptyList()
                } else {
                    statements
                }
            }
        }

        fun isValid(): Boolean {
            return surroundingStatement.isValid
        }
    }

    private var surroundingStatementData: SurroundingStatementData? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val lastAction = actions.lastOrNull() ?: return NoSuggestion
        if (surroundingStatementData != null
            && lastAction is PsiAction
            && !surroundingStatementData!!.isValid()
        ) {
            updateStatementReference(lastAction)
        }
        when (lastAction) {
            is ChildReplacedAction -> {
                val (parent, newChild, oldChild) = lastAction
                if (parent == null || newChild == null || oldChild == null) return NoSuggestion
                if (newChild.isIfStatement() && oldChild.text == "i"
                    || newChild.isForStatement() && oldChild.text == "fo"
                    || newChild.isWhileStatement() && oldChild.text == "whil"
                ) {
                    surroundingStatementData = SurroundingStatementData(newChild)
                } else if (surroundingStatementData != null) {
                    if (newChild is PsiErrorElement
                        && newChild.isMissedCloseBrace()
                    ) {
                        surroundingStatementData!!.apply {
                            expectedCloseBraceErrorElement = newChild
                            val statements = getStatementsFromBlock()
                            initialStatementsCount = statements.size
                            firstStatementInBlockText = statements.firstOrNull()?.text ?: ""
                        }
                    } else if (oldChild is PsiErrorElement
                        && oldChild.isMissedCloseBrace()
                        && surroundingStatementData!!.expectedCloseBraceErrorElement === oldChild
                    ) {
                        if (isStatementsSurrounded()) {
                            surroundingStatementData = null
                            return createSuggestion(
                                null,
                                createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                                suggestingActionDisplayName,
                                SUGGESTING_TIP_FILENAME
                            )
                        }
                        surroundingStatementData = null
                    }
                }
            }
            is ChildAddedAction -> {
                val (parent, newChild) = lastAction
                if (parent == null || newChild == null) return NoSuggestion
                if (newChild.isSurroundingStatement()) {
                    surroundingStatementData = SurroundingStatementData(newChild)
                }
            }
            else -> NoSuggestion
        }
        return NoSuggestion
    }

    private fun updateStatementReference(action: PsiAction) {
        val psiFile = action.parent?.containingFile ?: return
        val element = psiFile.findElementAt(surroundingStatementData!!.startOffset) ?: return
        val parent = element.parent ?: return
        if (parent.isSurroundingStatement()) {
            surroundingStatementData!!.surroundingStatement = parent
        } else {
            surroundingStatementData = null
        }
    }

    private fun isStatementsSurrounded(): Boolean {
        if (surroundingStatementData == null) return false
        with(surroundingStatementData!!) {
            val statements = getStatementsFromBlock()
            return statements.size in 1..initialStatementsCount
                    && statements.first().text == firstStatementInBlockText
        }
    }

    private fun PsiErrorElement.isMissedCloseBrace(): Boolean {
        return errorDescription == """'}' expected"""
                || errorDescription == """Missing '}"""
    }

    private fun PsiElement.isSurroundingStatement(): Boolean {
        return isIfStatement() || isForStatement() || isWhileStatement()
    }

    private fun PsiElement.isIfStatement(): Boolean {
        return this is PsiIfStatement || this is KtIfExpression
    }

    private fun PsiElement.isForStatement(): Boolean {
        return this is PsiForStatement || this is KtForExpression
    }

    private fun PsiElement.isWhileStatement(): Boolean {
        return this is PsiWhileStatement || this is KtWhileExpression
    }

    private fun PsiElement.isCodeBlock(): Boolean {
        return this is PsiCodeBlock || this is KtBlockExpression
    }

    override val suggestingActionDisplayName: String = "Surround with"
}