package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.*
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BeforeChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory

class SurroundWithSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not to use Surround With action?"
        const val SUGGESTING_ACTION_ID = "SurroundWith"
    }

    private class SurroundingStatementData(val surroundingStatement: PsiStatement) {
        var isLeftBraceAdded = false
        var expectedCloseBraceErrorElement: PsiErrorElement? = null
        var firstStatementInBlockText: String = ""
        var initialStatementsCount: Int = -1

        fun getStatementsFromBlock(): Array<PsiStatement> {
            val codeBlockStatement =
                surroundingStatement.children.lastOrNull()?.children?.lastOrNull() as? PsiCodeBlock
                    ?: return emptyArray()
            return codeBlockStatement.statements
        }
    }

    private var surroundingStatementData: SurroundingStatementData? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val lastAction = actions.lastOrNull() ?: return NoSuggestion
        when (lastAction) {
            is ChildReplacedAction -> {
                val (parent, newChild, oldChild) = lastAction
                if (parent == null || newChild == null || oldChild == null) return NoSuggestion
                if (newChild is PsiIfStatement && oldChild.text == "i"
                    || newChild is PsiForStatement && oldChild.text == "fo"
                    || newChild is PsiWhileStatement && oldChild.text == "whil"
                ) {
                    surroundingStatementData = SurroundingStatementData(newChild as PsiStatement)
                } else if (surroundingStatementData != null) {
                    if (newChild is PsiErrorElement
                        && newChild.errorDescription == """'}' expected"""
                        && surroundingStatementData!!.isLeftBraceAdded
                    ) {
                        surroundingStatementData!!.apply {
                            expectedCloseBraceErrorElement = newChild
                            val statements = getStatementsFromBlock()
                            initialStatementsCount = statements.size
                            firstStatementInBlockText = statements.firstOrNull()?.text ?: ""
                        }
                    } else if (oldChild is PsiErrorElement
                        && oldChild.errorDescription == """'}' expected"""
                        && surroundingStatementData!!.expectedCloseBraceErrorElement === oldChild
                    ) {
                        if (isStatementsSurrounded()) {
                            surroundingStatementData = null
                            return createSuggestion(
                                null,
                                createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                                getId()
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
                    surroundingStatementData = SurroundingStatementData(newChild as PsiStatement)
                }
            }
            is BeforeChildReplacedAction -> {
                val parent = lastAction.parent
                val newChild = lastAction.newChild
                if (surroundingStatementData != null
                    && parent === surroundingStatementData!!.surroundingStatement
                    && newChild is PsiBlockStatement
                ) {
                    surroundingStatementData!!.isLeftBraceAdded = true
                }
            }
            else -> NoSuggestion
        }
        return NoSuggestion
    }

    private fun isStatementsSurrounded(): Boolean {
        if (surroundingStatementData == null) return false
        with(surroundingStatementData!!) {
            val statements = getStatementsFromBlock()
            return isLeftBraceAdded
                    && statements.size in 1..initialStatementsCount
                    && statements.first().text == firstStatementInBlockText
        }
    }

    private fun PsiElement.isSurroundingStatement(): Boolean {
        return this is PsiIfStatement || this is PsiForStatement || this is PsiWhileStatement
    }

    override fun getId(): String = "Surround with suggester"
}