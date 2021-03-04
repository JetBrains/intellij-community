@file:Suppress("UnstableApiUsage")

package org.jetbrains.plugins.feature.suggester.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.log.statistics.CustomStringsValidationRule
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester

/**
 * feature_suggester group configuration
{
"event_id": [
"notification.showed",
"notification.dont_suggest",
"notification.thanks",
"notification.learn_more",
"suggestion_found"
],
"event_data": {
"suggester_id": [
"{util#feature_suggester_id}"
]
}
}
 */
class FeatureSuggestersStatisticsCollector {
    companion object {
        const val GROUP_ID = "feature_suggester"
        const val NOTIFICATION_SHOWED_EVENT_ID = "notification.showed"
        const val NOTIFICATION_DONT_SUGGEST_EVENT_ID = "notification.dont_suggest"
        const val NOTIFICATION_THANKS_EVENT_ID = "notification.thanks"
        const val NOTIFICATION_LEARN_MORE_EVENT_ID = "notification.learn_more"
        const val SUGGESTION_FOUND = "suggestion_found"
    }

    private val group = EventLogGroup(GROUP_ID, 1)
    private val events = mutableMapOf<String, EventId1<String?>>()

    fun sendStatistics(eventId: String, suggesterId: String) {
        val sendStatistics = Registry.get("feature.suggester.send.statistics").asBoolean()
        if (sendStatistics) {
            events.getOrPut(eventId) {
                group.registerEvent(
                    eventId,
                    EventFields.StringValidatedByCustomRule("suggester_id", "feature_suggester_id")
                )
            }.log(suggesterId)
        }
    }
}

class FeatureSuggesterIdRuleValidator : CustomStringsValidationRule(
    "feature_suggester_id",
    FeatureSuggester.suggesters.filter { getPluginInfo(it::class.java).isDevelopedByJetBrains() }
        .map(FeatureSuggester::id).toSet()
)
