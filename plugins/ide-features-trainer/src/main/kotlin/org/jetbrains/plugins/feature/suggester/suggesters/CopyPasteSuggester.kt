package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.ide.CopyPasteManager
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.changes.BeforeEditorCopyAction
import org.jetbrains.plugins.feature.suggester.changes.EditorCopyAction
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.history.UserAnActionsHistory
import java.awt.datatransfer.Transferable

class CopyPasteSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "You may use history of clipboard that can save your time: Ctrl + Shift + V"
        const val MIN_OCCURRENCE_INDEX = 1
        const val MAX_OCCURRENCE_INDEX = 2
        const val MAX_COPY_INTERVAL_TIME_MILLIS = 20000L
    }

    private val copyPasteManager = CopyPasteManager.getInstance()

    override fun getSuggestion(actions: UserActionsHistory, anActions: UserAnActionsHistory): Suggestion {
        when (val lastAction = anActions.lastOrNull()) {
            is BeforeEditorCopyAction -> {
                val contents: Array<Transferable> = copyPasteManager.allContents
                val occurrenceIndex = contents.indexOfFirst { it.asString() == lastAction.copiedText }
                if (occurrenceIndex in MIN_OCCURRENCE_INDEX..MAX_OCCURRENCE_INDEX) {
                    val prevAction = anActions.asIterable()
                        .findLast { (it as? EditorCopyAction)?.copiedText == lastAction.copiedText }
                        ?: return NoSuggestion
                    val delta = lastAction.timeMillis - prevAction.timeMillis
                    if (delta < MAX_COPY_INTERVAL_TIME_MILLIS) {
                        return createSuggestion(null, POPUP_MESSAGE)
                    }
                }
            }
        }
        return NoSuggestion
    }

    override fun getId(): String = "Copy Paste suggester"
}