package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.lang.Language
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorCopyAction
import org.jetbrains.plugins.feature.suggester.actions.EditorCopyAction
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.asString
import org.jetbrains.plugins.feature.suggester.createDocumentationSuggestion
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester.Companion.createMessageWithShortcut
import java.awt.datatransfer.Transferable
import java.util.concurrent.TimeUnit

class CopyPasteSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "You may use history of clipboard that can save your time."
        const val SUGGESTING_ACTION_ID = "PasteMultiple"
        const val SUGGESTING_DOC_URL =
            "https://www.jetbrains.com/help/idea/working-with-source-code.html#copy_paste"
        const val MIN_OCCURRENCE_INDEX = 1
        const val MAX_OCCURRENCE_INDEX = 2
        const val MAX_COPY_INTERVAL_TIME_MILLIS = 20000L
    }

    override val languages = listOf(Language.ANY.id)

    private val actionsSummary = actionsLocalSummary()
    private val copyPasteManager = CopyPasteManager.getInstance()

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        when (val action = actions.lastOrNull()) {
            is BeforeEditorCopyAction -> {
                val contents: Array<Transferable> = copyPasteManager.allContents
                val occurrenceIndex = contents.indexOfFirst { it.asString() == action.text }
                if (occurrenceIndex in MIN_OCCURRENCE_INDEX..MAX_OCCURRENCE_INDEX) {
                    val prevAction = actions.asIterable()
                        .findLast { (it as? EditorCopyAction)?.text == action.text }
                        ?: return NoSuggestion
                    val delta = action.timeMillis - prevAction.timeMillis
                    if (delta < MAX_COPY_INTERVAL_TIME_MILLIS) {
                        return createDocumentationSuggestion(
                            createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                            suggestingActionDisplayName,
                            SUGGESTING_DOC_URL
                        )
                    }
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

    override val id: String = "Paste from history"

    override val suggestingActionDisplayName: String = "Paste from history"
}
