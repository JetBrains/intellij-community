// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator

import com.intellij.internal.statistic.eventLog.EventLogSystemEvents
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.LogEventAction
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupRemoteDescriptors
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupsFilterRules
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogBuildProducer
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupRules
import com.intellij.internal.statistic.eventLog.validator.rules.utils.UtilRuleProducer
import com.intellij.internal.statistic.eventLog.validator.rules.utils.ValidationSimpleRuleFactory
import com.intellij.internal.statistic.eventLog.validator.storage.GlobalRulesHolder
import org.jetbrains.annotations.NotNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class SimpleSensitiveDataValidator<T : Comparable<T>>(initialMetadataContent: String,
                                                      private val buildProducer: EventLogBuildProducer<T>,
                                                      private val excludeFields: List<String> = emptyList(),
                                                      utilRulesProducer: UtilRuleProducer = ValidationSimpleRuleFactory.REJECTING_UTIL_URL_PRODUCER) {

  private val validationRuleFactory = ValidationSimpleRuleFactory(utilRulesProducer)
  private val eventsValidators: ConcurrentMap<String, EventGroupRules> = ConcurrentHashMap() // guarded by lock
  private lateinit var filterRules: EventGroupsFilterRules<T> // guarded by lock
  private val lock = Any()

  init {
    updateEventGroupRules(initialMetadataContent)
  }

  fun update(metadataContent: String) {
    updateEventGroupRules(metadataContent)
  }

  fun validateEvent(event: LogEvent): LogEvent? {
    synchronized(lock) {
      if (!filterRules.accepts(event.group.id, event.group.version, event.build)) {
        return null
      }
      val logEventAction = event.event
      val context = EventContext.create(logEventAction.id, logEventAction.data)
      val logEventGroup = event.group
      val groupRules = eventsValidators[logEventGroup.id]
      val validatedEventId = guaranteeCorrectEventId(context, groupRules)
      val validatedEventData = guaranteeCorrectEventData(context, groupRules)
      val validatedEvent = LogEventAction(validatedEventId, logEventAction.state, logEventAction.count)
      for (datum in validatedEventData) {
        validatedEvent.addData(datum.key, datum.value)
      }
      return LogEvent(event.session, event.build, event.bucket, event.time, logEventGroup.id, logEventGroup.version, event.recorderVersion,
                      validatedEvent)
    }
  }

  private fun updateEventGroupRules(metadataContent: String?) {
    synchronized(lock) {
      eventsValidators.clear()
      val descriptors = EventGroupRemoteDescriptors.create(metadataContent)
      eventsValidators.putAll(createValidators(descriptors))
      filterRules = EventGroupsFilterRules.create(descriptors, buildProducer)
    }
  }

  private fun createValidators(descriptors: EventGroupRemoteDescriptors): Map<String?, EventGroupRules> {
    val globalRulesHolder = GlobalRulesHolder(descriptors.rules)
    val groups = descriptors.groups
    return groups.associate { it.id to EventGroupRules.create(it, globalRulesHolder, validationRuleFactory, excludeFields) }
  }

  private fun guaranteeCorrectEventData(context: @NotNull EventContext,
                                        groupRules: EventGroupRules?): MutableMap<String, Any> {
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