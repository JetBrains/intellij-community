package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.TextFragment
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorTextRemovedAction
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.createDocumentationSuggestion
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.util.concurrent.TimeUnit

class UnwrapSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "Why not to use Unwrap action?"
        const val SUGGESTING_ACTION_ID = "Unwrap"
        const val SUGGESTING_DOC_URL =
            "https://www.jetbrains.com/help/idea/working-with-source-code.html#unwrap_remove_statement"
        const val MAX_TIME_MILLIS_BETWEEN_ACTIONS: Long = 7000L
    }

    private object State {
        var surroundingStatementStartOffset: Int = -1
        var closeBraceOffset: Int = -1
        var lastChangeTimeMillis: Long = 0L

        val isInitial: Boolean
            get() = surroundingStatementStartOffset == -1 && closeBraceOffset == -1

        fun isOutOfDate(newChangeTimeMillis: Long): Boolean {
            return newChangeTimeMillis - lastChangeTimeMillis > MAX_TIME_MILLIS_BETWEEN_ACTIONS
        }

        fun reset() {
            surroundingStatementStartOffset = -1
            closeBraceOffset = -1
            lastChangeTimeMillis = 0L
        }
    }

    override val languages = listOf("JAVA", "kotlin", "Python", "ECMAScript 6")

    private val actionsSummary = actionsLocalSummary()
    private val surroundingStatementStartRegex = Regex("""[ \n]*(if|for|while)[ \n]*\(.*\)[ \n]*\{[ \n]*""")

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        val action = actions.lastOrNull() ?: return NoSuggestion
        val language = action.language ?: return NoSuggestion
        val langSupport = LanguageSupport.getForLanguage(language) ?: return NoSuggestion
        if (action is BeforeEditorTextRemovedAction) {
            val text = action.textFragment.text
            when {
                text == "}" -> return langSupport.handleCloseBraceDeleted(action)
                text.matches(surroundingStatementStartRegex) -> {
                    return langSupport.handleStatementStartDeleted(action)
                }
                else -> State.reset()
            }
        }
        return NoSuggestion
    }

    private fun LanguageSupport.handleCloseBraceDeleted(action: BeforeEditorTextRemovedAction): Suggestion {
        when {
            State.isInitial -> handleCloseBraceDeletedFirst(action)
            State.closeBraceOffset != -1 -> {
                try {
                    if (State.checkCloseBraceDeleted(action)) return createSuggestion()
                } finally {
                    State.reset()
                }
            }
            else -> State.reset()
        }
        return NoSuggestion
    }

    private fun LanguageSupport.handleStatementStartDeleted(action: BeforeEditorTextRemovedAction): Suggestion {
        when {
            State.isInitial -> handleStatementStartDeletedFirst(action)
            State.surroundingStatementStartOffset != -1 -> {
                try {
                    if (State.checkStatementStartDeleted(action)) return createSuggestion()
                } finally {
                    State.reset()
                }
            }
            else -> State.reset()
        }
        return NoSuggestion
    }

    private fun LanguageSupport.handleCloseBraceDeletedFirst(action: BeforeEditorTextRemovedAction) {
        val curElement = action.psiFile?.findElementAt(action.caretOffset) ?: return
        val codeBlock = curElement.parent
        if (!isCodeBlock(codeBlock)) return
        val statements = getStatements(codeBlock)
        if (statements.isNotEmpty()) {
            val statement = getParentStatementOfBlock(codeBlock) ?: return
            if (isSurroundingStatement(statement)) {
                State.surroundingStatementStartOffset = statement.startOffset
                State.lastChangeTimeMillis = action.timeMillis
            }
        }
    }

    private fun State.checkStatementStartDeleted(action: BeforeEditorTextRemovedAction): Boolean {
        return !isOutOfDate(action.timeMillis) &&
            surroundingStatementStartOffset == action.textFragment.contentStartOffset
    }

    private fun LanguageSupport.handleStatementStartDeletedFirst(action: BeforeEditorTextRemovedAction) {
        val textFragment = action.textFragment
        val curElement = action.psiFile?.findElementAt(textFragment.contentStartOffset) ?: return
        val curStatement = curElement.parent
        if (isSurroundingStatement(curStatement)) {
            val codeBlock = getCodeBlock(curStatement) ?: return
            val statements = getStatements(codeBlock)
            if (statements.isNotEmpty()) {
                State.closeBraceOffset = curStatement.endOffset - textFragment.text.length - 1
                State.lastChangeTimeMillis = action.timeMillis
            }
        }
    }

    private fun State.checkCloseBraceDeleted(action: BeforeEditorTextRemovedAction): Boolean {
        return !isOutOfDate(action.timeMillis) && action.caretOffset == closeBraceOffset
    }

    private fun createSuggestion(): Suggestion {
        return createDocumentationSuggestion(
            createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
            suggestingActionDisplayName,
            SUGGESTING_DOC_URL
        )
    }

    override fun isSuggestionNeeded(minNotificationIntervalDays: Int): Boolean {
        return super.isSuggestionNeeded(
            actionsSummary,
            SUGGESTING_ACTION_ID,
            TimeUnit.DAYS.toMillis(minNotificationIntervalDays.toLong())
        )
    }

    private val TextFragment.contentStartOffset: Int
        get() {
            val countOfStartDelimiters = text.indexOfFirst { it != ' ' && it != '\n' }
            return startOffset + countOfStartDelimiters
        }

    private fun LanguageSupport.isSurroundingStatement(psiElement: PsiElement): Boolean {
        return isIfStatement(psiElement) || isForStatement(psiElement) || isWhileStatement(psiElement)
    }

    override val id: String = "Unwrap"

    override val suggestingActionDisplayName: String = "Unwrap"
}
