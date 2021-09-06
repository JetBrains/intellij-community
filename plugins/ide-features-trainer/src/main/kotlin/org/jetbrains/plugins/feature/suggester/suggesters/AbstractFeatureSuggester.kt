@file:Suppress("UnstableApiUsage")

package org.jetbrains.plugins.feature.suggester.suggesters

import org.jetbrains.plugins.feature.suggester.DocumentationSuggestion
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.TipSuggestion
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.getShortcutText
import org.jetbrains.plugins.feature.suggester.isRedoOrUndoRunning
import java.util.concurrent.TimeUnit

abstract class AbstractFeatureSuggester : FeatureSuggester {
    protected open val suggestingTipFileName: String? = null
    protected open val suggestingDocUrl: String? = null

    protected abstract val message: String
    protected abstract val suggestingActionId: String

    override fun isSuggestionNeeded(minNotificationIntervalDays: Int): Boolean {
        val actionStats = actionsLocalSummary().getActionsStats()
        val summary = actionStats[suggestingActionId]
        return if (summary == null) {
            true
        } else {
            val lastTimeUsed = summary.lastUsedTimestamp
            val delta = System.currentTimeMillis() - lastTimeUsed
            delta > TimeUnit.DAYS.toMillis(minNotificationIntervalDays.toLong())
        }
    }

    protected fun createSuggestion(): Suggestion {
        if (isRedoOrUndoRunning()) return NoSuggestion
        val fullMessage = "$message ${getShortcutText(suggestingActionId)}"
        return if (suggestingTipFileName != null) {
            TipSuggestion(fullMessage, id, suggestingTipFileName!!)
        } else if (suggestingDocUrl != null) {
            DocumentationSuggestion(fullMessage, id, suggestingDocUrl!!)
        } else error("Suggester must implement 'suggestingTipFileName' or 'suggestingDocLink' property.")
    }
}