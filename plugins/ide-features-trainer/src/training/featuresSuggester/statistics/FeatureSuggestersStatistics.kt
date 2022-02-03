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

class FeatureSuggesterStatistics : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    private const val GROUP_ID = "feature_suggester"
    private const val NOTIFICATION_SHOWED_EVENT_ID = "notification.showed"
    private const val NOTIFICATION_DONT_SUGGEST_EVENT_ID = "notification.dont_suggest"
    private const val NOTIFICATION_LEARN_MORE_EVENT_ID = "notification.learn_more"
    private const val SUGGESTION_FOUND = "suggestion_found"
    private const val SUGGESTER_ID_FIELD = "suggester_id"
    const val SUGGESTER_ID_VALIDATION_RULE = "feature_suggester_id"

    private val GROUP = EventLogGroup(GROUP_ID, 3)

    private val suggesterIdField = EventFields.StringValidatedByCustomRule(SUGGESTER_ID_FIELD, SUGGESTER_ID_VALIDATION_RULE)

    private val notificationShowedEvent = GROUP.registerEvent(NOTIFICATION_SHOWED_EVENT_ID, suggesterIdField)
    private val notificationDontSuggestEvent = GROUP.registerEvent(NOTIFICATION_DONT_SUGGEST_EVENT_ID, suggesterIdField)
    private val notificationLearnMoreEvent = GROUP.registerEvent(NOTIFICATION_LEARN_MORE_EVENT_ID, suggesterIdField)
    private val suggestionFoundEvent = GROUP.registerEvent(SUGGESTION_FOUND, suggesterIdField)

    fun logNotificationShowed(suggesterId: String) = sendStatistics(notificationShowedEvent, suggesterId)
    fun logNotificationDontSuggest(suggesterId: String) = sendStatistics(notificationDontSuggestEvent, suggesterId)
    fun logNotificationLearnMore(suggesterId: String) = sendStatistics(notificationLearnMoreEvent, suggesterId)
    fun logSuggestionFound(suggesterId: String) = sendStatistics(suggestionFoundEvent, suggesterId)

    private fun sendStatistics(event: EventId1<String?>, suggesterId: String) {
      if (Registry.get("feature.suggester.send.statistics").asBoolean()) {
        event.log(suggesterId)
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
