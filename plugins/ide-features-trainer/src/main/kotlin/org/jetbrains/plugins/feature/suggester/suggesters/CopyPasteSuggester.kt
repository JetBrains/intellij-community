package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.lang.Language
import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.plugins.feature.suggester.FeatureSuggesterBundle
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BeforeEditorCopyAction
import org.jetbrains.plugins.feature.suggester.actions.EditorCopyAction
import org.jetbrains.plugins.feature.suggester.asString
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import java.awt.datatransfer.Transferable

class CopyPasteSuggester : AbstractFeatureSuggester() {
    override val id: String = "Paste from history"
    override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("paste.from.history.name")

    override val message = FeatureSuggesterBundle.message("paste.from.history.message")
    override val suggestingActionId = "PasteMultiple"
    override val suggestingDocUrl = "https://www.jetbrains.com/help/idea/working-with-source-code.html#copy_paste"

    override val languages = listOf(Language.ANY.id)

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        when (val action = actions.lastOrNull()) {
            is BeforeEditorCopyAction -> {
                val contents: Array<Transferable> = CopyPasteManager.getInstance().allContents
                val occurrenceIndex = contents.indexOfFirst { it.asString() == action.text }
                if (occurrenceIndex in MIN_OCCURRENCE_INDEX..MAX_OCCURRENCE_INDEX) {
                    val prevAction = actions.asIterable()
                        .findLast { (it as? EditorCopyAction)?.text == action.text }
                        ?: return NoSuggestion
                    val delta = action.timeMillis - prevAction.timeMillis
                    if (delta < MAX_COPY_INTERVAL_TIME_MILLIS) {
                        return createSuggestion()
                    }
                }
            }
        }
        return NoSuggestion
    }

    companion object {
        const val MIN_OCCURRENCE_INDEX = 1
        const val MAX_OCCURRENCE_INDEX = 2
        const val MAX_COPY_INTERVAL_TIME_MILLIS = 20000L
    }
}
