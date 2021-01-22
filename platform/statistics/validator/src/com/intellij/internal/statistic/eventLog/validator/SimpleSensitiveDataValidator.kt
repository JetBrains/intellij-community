// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator

import com.intellij.internal.statistic.eventLog.EventLogSystemEvents
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.LogEventAction
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules

/**
 * Validates log event according to remote groups validation rules.
 * Used to ensure that no personal or proprietary data is recorded.
 *
 */
open class SimpleSensitiveDataValidator<S: BaseValidationRuleStorage<*>>(public val validationRulesStorage: S) {
  /**
   * @return null if the build or version failed validation,
   * otherwise returns validated event in which incorrect values are replaced with {@link ValidationResultType#getDescription()}.
   */
  fun validateEvent(event: LogEvent): LogEvent? {
    val logEventAction = event.event
    return validate(event.group.id, event.group.version, event.build, event.session, event.bucket, event.time,
                    event.recorderVersion,
                    logEventAction.id,
                    logEventAction.data, logEventAction.state,
                    logEventAction.count)
  }

  fun validate(groupId: String,
               groupVersion: String,
               build: String,
               sessionId: String,
               bucket: String,
               eventTime: Long,
               recorderVersion: String,
               eventId: String,
               data: Map<String, Any>,
               isState: Boolean,
               count: Int = 1): LogEvent? {
    val (groupRules, versionFilter) = validationRulesStorage.getGroupValidators(groupId)
    if (versionFilter != null && !versionFilter.accepts(groupId, groupVersion, build)) {
      return null
    }
    val context = EventContext.create(eventId, data)
    val validatedEventId = guaranteeCorrectEventId(context, groupRules)
    val validatedEventData = guaranteeCorrectEventData(context, groupRules)
    val validatedEvent = LogEventAction(validatedEventId, isState, count)
    for (datum in validatedEventData) {
      validatedEvent.addData(datum.key, datum.value)
    }
    return LogEvent(sessionId, build, bucket, eventTime, groupId, groupVersion, recorderVersion, validatedEvent)
  }

  protected open fun guaranteeCorrectEventId(context: EventContext, groupRules: EventGroupRules?): String {
    if (validationRulesStorage.isUnreachable()) return ValidationResultType.UNREACHABLE_METADATA.description
    if (EventLogSystemEvents.SYSTEM_EVENTS.contains(context.eventId)) return context.eventId
    val validationResultType = validateEvent(context, groupRules)
    return if (validationResultType == ValidationResultType.ACCEPTED) context.eventId else validationResultType.description
  }

  protected open fun guaranteeCorrectEventData(context: EventContext,
                                               groupRules: EventGroupRules?): MutableMap<String, Any> {
    val validatedData: MutableMap<String, Any> = HashMap()
    for ((key, entryValue) in context.eventData) {
      validatedData[key] = validateEventData(context, groupRules, key, entryValue)
    }
    return validatedData
  }

  protected fun validateEventData(context: EventContext,
                                  groupRules: EventGroupRules?,
                                  key: String,
                                  entryValue: Any): Any {
    if (validationRulesStorage.isUnreachable()) return ValidationResultType.UNREACHABLE_METADATA.description
    return if (groupRules == null) ValidationResultType.UNDEFINED_RULE.description
    else groupRules.validateEventData(key, entryValue, context)
  }

  companion object {
    @JvmStatic
    fun validateEvent(context: EventContext, groupRules: EventGroupRules?): ValidationResultType {
      return if (groupRules == null || !groupRules.areEventIdRulesDefined()) {
        ValidationResultType.UNDEFINED_RULE // there are no rules (eventId and eventData) to validate
      }
      else groupRules.validateEventId(context)
    }
  }

}