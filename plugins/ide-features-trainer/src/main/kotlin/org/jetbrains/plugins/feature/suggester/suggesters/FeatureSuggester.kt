package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.keymap.KeymapUtil
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport

@Suppress("UnstableApiUsage")
interface FeatureSuggester {

    companion object {
        val EP_NAME: ExtensionPointName<FeatureSuggester> =
            ExtensionPointName.create("org.intellij.featureSuggester.featureSuggester")

        val suggesters: List<FeatureSuggester>
            get() = EP_NAME.extensionList

        fun getSuggestingActionNames(): List<String> {
            return suggesters.map(FeatureSuggester::suggestingActionDisplayName)
        }

        fun createMessageWithShortcut(actionId: String, suggestionMessage: String): String {
            val shortcut = KeymapUtil.getShortcutText(actionId)
            return if (shortcut == "<no shortcut>") {
                "$suggestionMessage You can bind this action to convenient shortcut."
            } else {
                "$suggestionMessage $shortcut"
            }
        }
    }

    var langSupport: LanguageSupport

    fun getSuggestion(actions: UserActionsHistory): Suggestion

    fun isSuggestionNeeded(minNotificationIntervalDays: Int): Boolean

    fun isSuggestionNeeded(
        actionsSummary: ActionsLocalSummary,
        suggestingActionId: String,
        minNotificationIntervalMillis: Long
    ): Boolean {
        val actionStats = actionsSummary.getActionsStats()
        val summary = actionStats[suggestingActionId]
        return if (summary == null) {
            true
        } else {
            val lastTimeUsed = summary.lastUsedTimestamp
            val delta = System.currentTimeMillis() - lastTimeUsed
            delta > minNotificationIntervalMillis
        }
    }

    val id: String

    val suggestingActionDisplayName: String
}