@file:Suppress("UnstableApiUsage")

package training.featuresSuggester.suggesters

import training.featuresSuggester.*
import training.featuresSuggester.settings.FeatureSuggesterSettings

abstract class AbstractFeatureSuggester : FeatureSuggester {
  protected open val suggestingTipFileName: String? = null
  protected open val suggestingDocUrl: String? = null

  protected abstract val message: String
  protected abstract val suggestingActionId: String

  override fun isSuggestionNeeded(): Boolean {
    val summary = actionsLocalSummary().getActionStatsById(suggestingActionId) ?: return true
    val lastTimeUsed = summary.lastUsedTimestamp
    val oldestWorkingDayStart = FeatureSuggesterSettings.instance().getOldestWorkingDayStartMillis(minSuggestingIntervalDays)
    return lastTimeUsed < oldestWorkingDayStart
  }

  protected fun createSuggestion(): Suggestion {
    if (isRedoOrUndoRunning()) return NoSuggestion
    val fullMessage = "$message ${getShortcutText(suggestingActionId)}"
    return if (suggestingTipFileName != null) {
      TipSuggestion(fullMessage, id, suggestingTipFileName!!)
    }
    else if (suggestingDocUrl != null) {
      DocumentationSuggestion(fullMessage, id, suggestingDocUrl!!)
    }
    else error("Suggester must implement 'suggestingTipFileName' or 'suggestingDocLink' property.")
  }
}
