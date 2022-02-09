@file:Suppress("UnstableApiUsage")

package training.featuresSuggester.suggesters

import training.featuresSuggester.*
import training.featuresSuggester.settings.FeatureSuggesterSettings
import java.util.concurrent.TimeUnit

abstract class AbstractFeatureSuggester : FeatureSuggester {
  protected open val suggestingTipFileName: String? = null
  protected open val suggestingDocUrl: String? = null

  protected abstract val message: String
  protected abstract val suggestingActionId: String

  /**
   * There are following conditions that must be met to show suggestion:
   * 1. The suggesting action must not have been used during the last [minSuggestingIntervalDays] working days
   * 2. This suggestion must not have been shown during the last 24 hours
   */
  override fun isSuggestionNeeded() = !isSuggestingActionUsedRecently() && !isSuggestionShownRecently()

  private fun isSuggestingActionUsedRecently(): Boolean {
    val summary = actionsLocalSummary().getActionStatsById(suggestingActionId) ?: return false
    val lastTimeUsed = summary.lastUsedTimestamp
    val oldestWorkingDayStart = FeatureSuggesterSettings.instance().getOldestWorkingDayStartMillis(minSuggestingIntervalDays)
    return lastTimeUsed > oldestWorkingDayStart
  }

  private fun isSuggestionShownRecently(): Boolean {
    val lastTimeShown = FeatureSuggesterSettings.instance().getSuggestionLastShownTime(id)
    val delta = System.currentTimeMillis() - lastTimeShown
    return delta < TimeUnit.DAYS.toMillis(1L)
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
