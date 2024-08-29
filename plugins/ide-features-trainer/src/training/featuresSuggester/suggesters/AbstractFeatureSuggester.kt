// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.featuresSuggester.suggesters

import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapUtil
import org.jetbrains.annotations.Nls
import training.featuresSuggester.*
import training.featuresSuggester.settings.FeatureSuggesterSettings
import training.featuresSuggester.statistics.FeatureSuggesterStatistics
import java.util.concurrent.TimeUnit

abstract class AbstractFeatureSuggester : FeatureSuggester {
  protected open val suggestingTipId: String? = null
  protected open val suggestingDocUrl: String? = null

  protected abstract val message: String
  protected abstract val suggestingActionId: String

  /**
   * There are following conditions that must be met to show suggestion:
   * 1. The suggesting action must not have been used during the last [minSuggestingIntervalDays] working days
   * 2. This suggestion must not have been shown during the last 24 hours
   */
  override fun isSuggestionNeeded() = !isSuggestingActionUsedRecently() && !isSuggestionShownRecently()

  override fun logStatisticsThatSuggestionIsFound(suggestion: Suggestion) {
    val daysPassedFromLastUsage = actionSummary()?.lastUsedTimestamp?.let { (System.currentTimeMillis() - it) / TimeUnit.DAYS.toMillis(1L) } ?: -1
    FeatureSuggesterStatistics.logSuggestionFound(id, !isSuggestingActionUsedRecently(), daysPassedFromLastUsage.toInt())
  }

  private fun isSuggestingActionUsedRecently(): Boolean {
    val summary = actionSummary() ?: return false
    val lastTimeUsed = summary.lastUsedTimestamp
    val oldestWorkingDayStart = FeatureSuggesterSettings.instance().getOldestWorkingDayStartMillis(minSuggestingIntervalDays)
    return lastTimeUsed > oldestWorkingDayStart
  }

  private fun actionSummary() = service<ActionsLocalSummary>().getActionStatsById(suggestingActionId)

  private fun isSuggestionShownRecently(): Boolean {
    val lastTimeShown = FeatureSuggesterSettings.instance().getSuggestionLastShownTime(id)
    val delta = System.currentTimeMillis() - lastTimeShown
    return delta < TimeUnit.DAYS.toMillis(1L)
  }

  protected fun createSuggestion(): Suggestion {
    if (isRedoOrUndoRunning()) return NoSuggestion
    val fullMessage = "$message ${getShortcutText(suggestingActionId)}"
    return if (suggestingTipId != null) {
      TipSuggestion(fullMessage, id, suggestingTipId!!)
    }
    else if (suggestingDocUrl != null) {
      DocumentationSuggestion(fullMessage, id, suggestingDocUrl!!)
    }
    else error("Suggester must implement 'suggestingTipFileName' or 'suggestingDocLink' property.")
  }

  private fun isRedoOrUndoRunning(): Boolean {
    val commandName = CommandProcessor.getInstance().currentCommandName
    return commandName != null && (commandName.startsWith(ActionsBundle.message("action.redo.description", ""))
                                   || commandName.startsWith(ActionsBundle.message("action.undo.description", "")))
  }

  @Nls
  open fun getShortcutText(actionId: String): String {
    val shortcut = KeymapUtil.getShortcutText(actionId)
    return if (shortcut == "<no shortcut>") {
      FeatureSuggesterBundle.message("shortcut.not.found.message")
    }
    else {
      FeatureSuggesterBundle.message("shortcut", shortcut)
    }
  }
}
