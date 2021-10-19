@file:Suppress("UnstableApiUsage")

package training.featuresSuggester.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.util.registry.Registry
import training.featuresSuggester.statistics.FeatureSuggesterStatistics.Companion.SUGGESTER_ID_VALIDATION_RULE
import training.featuresSuggester.suggesters.FeatureSuggester

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
class FeatureSuggesterStatistics : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    const val GROUP_ID = "feature_suggester"
    const val NOTIFICATION_SHOWED_EVENT_ID = "notification.showed"
    const val NOTIFICATION_DONT_SUGGEST_EVENT_ID = "notification.dont_suggest"
    const val NOTIFICATION_THANKS_EVENT_ID = "notification.thanks"
    const val NOTIFICATION_LEARN_MORE_EVENT_ID = "notification.learn_more"
    const val SUGGESTION_FOUND = "suggestion_found"
    const val SUGGESTER_ID_FIELD = "suggester_id"
    const val SUGGESTER_ID_VALIDATION_RULE = "feature_suggester_id"

    private val GROUP = EventLogGroup(GROUP_ID, 2)
    private val EVENTS = mutableMapOf<String, EventId1<String?>>()

    fun sendStatistics(eventId: String, suggesterId: String) {
      val sendStatistics = Registry.get("feature.suggester.send.statistics").asBoolean()
      if (sendStatistics) {
        EVENTS.getOrPut(eventId) {
          GROUP.registerEvent(
            eventId,
            EventFields.StringValidatedByCustomRule(SUGGESTER_ID_FIELD, SUGGESTER_ID_VALIDATION_RULE)
          )
        }.log(suggesterId)
      }
    }
  }
}

class FeatureSuggesterIdRuleValidator : CustomValidationRule() {
  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val suggesterIds = FeatureSuggester.suggesters.filter { getPluginInfo(it::class.java).isDevelopedByJetBrains() }
      .map(FeatureSuggester::id)
    return if (suggesterIds.contains(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }

  override fun acceptRuleId(ruleId: String?) = ruleId == SUGGESTER_ID_VALIDATION_RULE
}
