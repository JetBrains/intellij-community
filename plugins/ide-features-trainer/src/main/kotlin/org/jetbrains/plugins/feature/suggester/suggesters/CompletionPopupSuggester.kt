package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.psi.PsiComment
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.*
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.util.concurrent.TimeUnit

class CompletionPopupSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "You may use shortcut to call completion popup."
        const val SUGGESTING_ACTION_ID = "CodeCompletion"
        const val SUGGESTING_TIP_FILENAME = "neue-CodeCompletion.html"
    }

    private data class EditedStatementData(val dotOffset: Int) {
        var completionStarted: Boolean = false

        fun isAroundDot(offset: Int): Boolean {
            return offset in dotOffset..(dotOffset + 7)
        }
    }

    private val actionsSummary = actionsLocalSummary()
    override lateinit var langSupport: LanguageSupport

    private var editedStatementData: EditedStatementData? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        when (val lastAction = actions.lastOrNull()) {
            is EditorFocusGainedAction -> return NoSuggestion
            is BeforeEditorTextRemovedAction -> {
                if (lastAction.text == ".") {
                    editedStatementData = createEditedStatementData(lastAction, lastAction.offset)
                }
            }
            is BeforeEditorTextInsertedAction -> {
                if (editedStatementData != null &&
                    lastAction.text == "."
                    && lastAction.offset == editedStatementData!!.dotOffset
                ) {
                    editedStatementData!!.completionStarted = true
                }
            }
            is BeforeCompletionChooseItemAction -> {
                if (editedStatementData?.isAroundDot(lastAction.offset) == true) {
                    return createTipSuggestion(
                        createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                        suggestingActionDisplayName,
                        SUGGESTING_TIP_FILENAME
                    )
                }
            }
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

    @Suppress("DuplicatedCode")
    private fun createEditedStatementData(action: EditorAction, offset: Int): EditedStatementData? {
        val psiFile = action.psiFileRef.get() ?: return null
        val curElement = psiFile.findElementAt(offset) ?: return null
        return if (curElement.getParentByPredicate(langSupport::isLiteralExpression) == null
            && curElement.getParentOfType<PsiComment>() == null
        ) {
            EditedStatementData(offset)
        } else {
            null
        }
    }

    override val id: String = "Completion"

    override val suggestingActionDisplayName: String = "Show completion popup"
}