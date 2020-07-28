@file:Suppress("UnstableApiUsage")

package org.jetbrains.plugins.feature.suggester.statistics

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.vcs.log.statistics.CustomStringsValidationRule
import org.jetbrains.plugins.feature.suggester.FeatureSuggester

/**
 * feature_suggester group configuration
{
    "event_id": [
        "notification.showed",
        "notification.dont_suggest",
        "notification.thanks",
        "notification.learn_more"
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
    }

    fun sendStatistics(eventId: String, suggesterId: String) {
        FUCounterUsageLogger.getInstance().logEvent(
            GROUP_ID,
            eventId,
            FeatureUsageData().addData("suggester_id", suggesterId)
        )
    }
}

class FeatureSuggesterIdRuleValidator : CustomStringsValidationRule(
    "feature_suggester_id",
    FeatureSuggester.suggesters.map(FeatureSuggester::suggestingActionDisplayName).toSet()
)