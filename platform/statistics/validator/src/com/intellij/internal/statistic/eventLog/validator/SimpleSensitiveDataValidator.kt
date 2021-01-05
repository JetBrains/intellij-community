// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator

import com.intellij.internal.statistic.eventLog.EventLogSystemEvents
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.LogEventAction
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationRulesStorage
import org.jetbrains.annotations.NotNull

class SimpleSensitiveDataValidator(private val myRulesStorage: ValidationRulesStorage) {
  fun validateEvent(event: LogEvent): LogEvent {
    val logEventAction = event.event
    val context = EventContext.create(logEventAction.id, logEventAction.data)
    val logEventGroup = event.group
    val validatedEventId = guaranteeCorrectEventId(logEventGroup.id, context)
    val validatedEventData = guaranteeCorrectEventData(logEventGroup.id, context)
    val validatedEvent = LogEventAction(validatedEventId, logEventAction.state, logEventAction.count)
    for (datum in validatedEventData) {
      event.event.addData(datum.key, datum.value)
    }
    return LogEvent(event.session, event.build, event.bucket, event.time, logEventGroup.id, logEventGroup.version, event.recorderVersion,
                    validatedEvent)
  }

  fun guaranteeCorrectEventId(groupId: @NotNull String,
                                       context: @NotNull EventContext): String {
    return guaranteeCorrectEventId(context, myRulesStorage.getGroupRules(groupId))
  }

  fun guaranteeCorrectEventData(groupId: @NotNull String,
                                         context: @NotNull EventContext): MutableMap<String, Any> {
    val groupRules = myRulesStorage.getGroupRules(groupId)
    val validatedData: MutableMap<String, Any> = HashMap()
    for ((key, entryValue) in context.eventData) {
      validatedData[key] = validateEventData(context, groupRules, key, entryValue)
    }
    return validatedData
  }

  private fun validateEventData(context: EventContext,
                                groupRules: EventGroupRules?,
                                key: String,
                                entryValue: Any): Any {
    if (groupRules == null) return ValidationResultType.UNDEFINED_RULE.description
    return groupRules.validateEventData(key, entryValue, context)
  }

  companion object {
    @JvmStatic
    fun validateEvent(context: EventContext, groupRules: EventGroupRules?): ValidationResultType {
      return if (groupRules == null || !groupRules.areEventIdRulesDefined()) {
        ValidationResultType.UNDEFINED_RULE // there are no rules (eventId and eventData) to validate
      }
      else groupRules.validateEventId(context)
    }


    @JvmStatic
    fun guaranteeCorrectEventId(context: EventContext, eventGroupRules: EventGroupRules?): String {
      if (EventLogSystemEvents.SYSTEM_EVENTS.contains(context.eventId)) return context.eventId
      val validationResultType = validateEvent(context, eventGroupRules)
      return if (validationResultType == ValidationResultType.ACCEPTED) context.eventId else validationResultType.description
    }
  }

}