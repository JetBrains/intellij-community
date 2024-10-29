package training.featuresSuggester.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import training.featuresSuggester.suggesters.FeatureSuggester

object FeatureSuggesterStatistics : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private const val GROUP_ID = "feature_suggester"
  private const val NOTIFICATION_SHOWED_EVENT_ID = "notification.showed"
  private const val NOTIFICATION_DONT_SUGGEST_EVENT_ID = "notification.dont_suggest"
  private const val NOTIFICATION_LEARN_MORE_EVENT_ID = "notification.learn_more"
  private const val SUGGESTION_FOUND = "suggestion_found"
  private const val SUGGESTER_ID_FIELD = "suggester_id"
  private const val SUGGESTION_WOULD_BE_SHOWN_FIELD = "suggestion_would_be_shown"
  private const val DAYS_PASSED_LAST_USED_FIELD = "days_passed_last_used"
  const val SUGGESTER_ID_VALIDATION_RULE = "feature_suggester_id"

  private val GROUP = EventLogGroup(GROUP_ID, 5)

  private val suggesterIdField = EventFields.StringValidatedByCustomRule(SUGGESTER_ID_FIELD, FeatureSuggesterIdRuleValidator::class.java)
  private val suggestionWouldBeShownField = EventFields.Boolean(SUGGESTION_WOULD_BE_SHOWN_FIELD)
  private val daysPassedLastUsedField = EventFields.Int(DAYS_PASSED_LAST_USED_FIELD)

  private val notificationShowedEvent = GROUP.registerEvent(NOTIFICATION_SHOWED_EVENT_ID, suggesterIdField)
  private val notificationDontSuggestEvent = GROUP.registerEvent(NOTIFICATION_DONT_SUGGEST_EVENT_ID, suggesterIdField)
  private val notificationLearnMoreEvent = GROUP.registerEvent(NOTIFICATION_LEARN_MORE_EVENT_ID, suggesterIdField)

  private val suggestionFoundEvent = GROUP.registerEvent(SUGGESTION_FOUND, suggesterIdField, suggestionWouldBeShownField, daysPassedLastUsedField)

  fun logNotificationShowed(suggesterId: String) = notificationShowedEvent.log(suggesterId)
  fun logNotificationDontSuggest(suggesterId: String) = notificationDontSuggestEvent.log(suggesterId)
  fun logNotificationLearnMore(suggesterId: String) = notificationLearnMoreEvent.log(suggesterId)

  fun logSuggestionFound(suggesterId: String, suggestionWouldBeShown: Boolean, daysPassedFromLastUsage: Int) {
      suggestionFoundEvent.log(suggesterId, suggestionWouldBeShown, daysPassedFromLastUsage)
  }
}

class FeatureSuggesterIdRuleValidator : CustomValidationRule() {
  override fun getRuleId(): String = FeatureSuggesterStatistics.SUGGESTER_ID_VALIDATION_RULE

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val suggesterIds = FeatureSuggester.suggesters.filter { getPluginInfo(it::class.java).isDevelopedByJetBrains() }
      .map(FeatureSuggester::id)
    return if (suggesterIds.contains(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }
}
