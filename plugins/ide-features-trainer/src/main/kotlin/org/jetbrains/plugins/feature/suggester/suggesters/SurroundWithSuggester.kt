package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BeforeChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.PsiAction
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.createTipSuggestion
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.util.concurrent.TimeUnit

class SurroundWithSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not to use Surround With action?"
        const val SUGGESTING_ACTION_ID = "SurroundWith"
        const val SUGGESTING_TIP_FILENAME = "neue-SurroundWith.html"
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
        val action = actions.lastOrNull() ?: return NoSuggestion
        if (surroundingStatementData != null
            && action is PsiAction
            && !surroundingStatementData!!.isValid()
        ) {
            updateStatementReference(action)
        }
        when (action) {
            is ChildReplacedAction -> {
                if (langSupport.isIfStatement(action.newChild) && action.oldChild.text == "i"
                    || langSupport.isForStatement(action.newChild) && action.oldChild.text == "fo"
                    || langSupport.isWhileStatement(action.newChild) && action.oldChild.text == "whil"
                ) {
                    surroundingStatementData = SurroundingStatementData(action.newChild)
                }
            }
            is BeforeChildReplacedAction -> {
                if (surroundingStatementData == null) return NoSuggestion
                with(action) {
                    if (newChild is PsiErrorElement
                        && newChild.isMissedCloseBrace()
                    ) {
                        surroundingStatementData!!.run {
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
                            return createTipSuggestion(
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
                if (action.newChild.isSurroundingStatement()) {
                    surroundingStatementData = SurroundingStatementData(action.newChild)
                }
            }
            else -> NoSuggestion
        }
        return NoSuggestion
    }

    override fun isSuggestionNeeded(minNotificationIntervalDays: Int): Boolean {
        return super.isSuggestionNeeded(
            actionsSummary,
            SUGGESTING_ACTION_ID,
            TimeUnit.DAYS.toMillis(minNotificationIntervalDays.toLong())
        )
    }

    private fun updateStatementReference(action: PsiAction) {
        val psiFile = action.parent.containingFile ?: return
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