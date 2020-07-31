package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.PsiAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.util.concurrent.TimeUnit

class SurroundWithSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not to use Surround With action?"
        const val SUGGESTING_ACTION_ID = "SurroundWith"
        const val SUGGESTING_TIP_FILENAME = "neue-SurroundWith.html"
        const val MIN_NOTIFICATION_INTERVAL_DAYS = 14
    }

    private val actionsSummary = actionsLocalSummary()

    private class SurroundingStatementData(var surroundingStatement: PsiElement) {
        val startOffset: Int = surroundingStatement.startOffset
        var expectedCloseBraceErrorElement: PsiErrorElement? = null
        var firstStatementInBlockText: String = ""
        var initialStatementsCount: Int = -1

        fun isValid(): Boolean {
            return surroundingStatement.isValid
        }
    }

    override lateinit var langSupport: LanguageSupport

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
                if (langSupport.isIfStatement(newChild) && oldChild.text == "i"
                    || langSupport.isForStatement(newChild) && oldChild.text == "fo"
                    || langSupport.isWhileStatement(newChild) && oldChild.text == "whil"
                ) {
                    surroundingStatementData = SurroundingStatementData(newChild)
                } else if (surroundingStatementData != null) {
                    if (newChild is PsiErrorElement
                        && newChild.isMissedCloseBrace()
                    ) {
                        surroundingStatementData!!.apply {
                            expectedCloseBraceErrorElement = newChild
                            val statements = surroundingStatement.getStatements()
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

    override fun isSuggestionNeeded(): Boolean {
        return super.isSuggestionNeeded(
            actionsSummary,
            SUGGESTING_ACTION_ID,
            TimeUnit.DAYS.toMillis(MIN_NOTIFICATION_INTERVAL_DAYS.toLong())
        )
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
            val statements = surroundingStatement.getStatements()
            return statements.size in 1..initialStatementsCount
                    && statements.first().text == firstStatementInBlockText
        }
    }

    private fun PsiElement.getStatements(): List<PsiElement> {
        val codeBlock = langSupport.getCodeBlock(this)
        return if (codeBlock != null) {
            langSupport.getStatements(codeBlock)
        } else {
            emptyList()
        }
    }

    private fun PsiErrorElement.isMissedCloseBrace(): Boolean {
        return errorDescription == """'}' expected"""
                || errorDescription == """Missing '}"""
    }

    private fun PsiElement.isSurroundingStatement(): Boolean {
        return langSupport.isIfStatement(this)
                || langSupport.isForStatement(this)
                || langSupport.isWhileStatement(this)
    }

    override val id: String = "Surround with"

    override val suggestingActionDisplayName: String = "Surround with"
}