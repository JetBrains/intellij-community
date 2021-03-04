package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.actions.ChildrenChangedAction
import org.jetbrains.plugins.feature.suggester.actions.EditorTextInsertedAction
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
        const val MAX_TIME_MILLIS_BETWEEN_ACTIONS: Long = 8000L
    }

    private object State {
        var surroundingStatement: PsiElement? = null
        var surroundingStatementStartOffset: Int = -1
        var firstStatementInBlockText: String = ""
        var isLeftBraceAdded: Boolean = false
        var isRightBraceAdded: Boolean = false
        var lastChangeTimeMillis: Long = 0L

        fun applySurroundingStatementAddition(statement: PsiElement, timeMillis: Long) {
            reset()
            surroundingStatement = statement
            surroundingStatementStartOffset = statement.startOffset
            lastChangeTimeMillis = timeMillis
        }

        fun applyBraceAddition(timeMillis: Long, braceType: String) {
            lastChangeTimeMillis = timeMillis
            if (braceType == "{") {
                isLeftBraceAdded = true
            } else {
                isRightBraceAdded = true
            }
        }

        fun isOutOfDate(newChangeTimeMillis: Long): Boolean {
            return lastChangeTimeMillis != 0L &&
                newChangeTimeMillis - lastChangeTimeMillis > MAX_TIME_MILLIS_BETWEEN_ACTIONS
        }

        fun reset() {
            surroundingStatement = null
            surroundingStatementStartOffset = -1
            firstStatementInBlockText = ""
            isLeftBraceAdded = false
            isRightBraceAdded = false
            lastChangeTimeMillis = 0L
        }
    }

    private val actionsSummary = actionsLocalSummary()
    override lateinit var langSupport: LanguageSupport

    @Suppress("NestedBlockDepth")
    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val action = actions.lastOrNull() ?: return NoSuggestion
        if (State.surroundingStatement?.isValid == false && action is PsiAction) {
            State.tryToUpdateSurroundingStatement(action)
        }
        when (action) {
            is ChildReplacedAction -> {
                @Suppress("ComplexCondition")
                if (langSupport.isIfStatement(action.newChild) && action.oldChild.text == "i" ||
                    langSupport.isForStatement(action.newChild) && action.oldChild.text == "fo" ||
                    langSupport.isWhileStatement(action.newChild) && action.oldChild.text == "whil"
                ) {
                    State.applySurroundingStatementAddition(action.newChild, action.timeMillis)
                }
            }
            is ChildAddedAction -> {
                if (action.newChild.isSurroundingStatement()) {
                    State.applySurroundingStatementAddition(action.newChild, action.timeMillis)
                }
            }
            is ChildrenChangedAction -> {
                if (State.surroundingStatement == null) return NoSuggestion
                val textInsertedAction = actions.findLastTextInsertedAction() ?: return NoSuggestion
                val text = textInsertedAction.text
                if (text.contains("{") || text.contains("}")) {
                    val psiFile = action.parent as? PsiFile ?: return NoSuggestion
                    if (State.isOutOfDate(action.timeMillis) || text != "{" && text != "}") {
                        State.reset()
                    } else if (text == "{") {
                        if (State.isLeftBraceAdded) {
                            State.reset()
                        } else if (State.isBraceAddedToStatement(psiFile, textInsertedAction.caretOffset)) {
                            State.applyBraceAddition(action.timeMillis, "{")
                            State.saveFirstStatementInBlock()
                        }
                    } else if (text == "}") {
                        if (State.isLeftBraceAdded &&
                            State.isBraceAddedToStatement(psiFile, textInsertedAction.caretOffset)
                        ) {
                            State.applyBraceAddition(action.timeMillis, "}")
                            if (State.isStatementsSurrounded()) {
                                State.reset()
                                return createTipSuggestion(
                                    createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                                    suggestingActionDisplayName,
                                    SUGGESTING_TIP_FILENAME
                                )
                            }
                        }
                        State.reset()
                    }
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

    private fun State.tryToUpdateSurroundingStatement(action: PsiAction) {
        val psiFile = action.parent.containingFile ?: return
        val element = psiFile.findElementAt(surroundingStatementStartOffset) ?: return
        val parent = element.parent ?: return
        if (parent.isSurroundingStatement()) {
            surroundingStatement = parent
        }
    }

    private fun UserActionsHistory.findLastTextInsertedAction(): EditorTextInsertedAction? {
        return asIterable().findLast { it is EditorTextInsertedAction } as? EditorTextInsertedAction
    }

    private fun State.isBraceAddedToStatement(psiFile: PsiFile, offset: Int): Boolean {
        val curElement = psiFile.findElementAt(offset) ?: return false
        return curElement.parent === langSupport.getCodeBlock(surroundingStatement!!)
    }

    private fun State.saveFirstStatementInBlock() {
        val statements = surroundingStatement!!.getStatements()
        if (statements.isNotEmpty()) {
            firstStatementInBlockText = statements.first().text
        }
    }

    private fun State.isStatementsSurrounded(): Boolean {
        if (surroundingStatement?.isValid == false ||
            !isLeftBraceAdded ||
            !isRightBraceAdded
        ) {
            return false
        }
        val statements = surroundingStatement!!.getStatements()
        return statements.isNotEmpty() &&
            statements.first().text == firstStatementInBlockText
    }

    private fun PsiElement.getStatements(): List<PsiElement> {
        val codeBlock = langSupport.getCodeBlock(this)
        return if (codeBlock != null) {
            langSupport.getStatements(codeBlock)
        } else {
            emptyList()
        }
    }

    private fun PsiElement.isSurroundingStatement(): Boolean {
        return langSupport.isIfStatement(this) ||
            langSupport.isForStatement(this) ||
            langSupport.isWhileStatement(this)
    }

    override val id: String = "Surround with"

    override val suggestingActionDisplayName: String = "Surround with"
}
