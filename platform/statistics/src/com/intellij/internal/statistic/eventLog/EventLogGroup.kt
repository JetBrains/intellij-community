// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.eventLog.events.*

/**
 * Best practices:
 * - Prefer a bigger group with many (related) event types to many small groups of 1-2 events each
 * - Prefer shorter group names; avoid common prefixes (such as "statistics.")
 */
class EventLogGroup(val id: String, val version: Int) {
  private val registeredEventIds = mutableSetOf<String>()
  private val registeredEvents = mutableListOf<BaseEventId>()

  val events: List<BaseEventId> get() = registeredEvents

  private fun addToRegisteredEvents(eventId: BaseEventId) {
    registeredEvents.add(eventId)
    registeredEventIds.add(eventId.eventId)
  }

  /**
   * New style API to record IDE events (e.g. invoked action, opened dialog) or state.
   *
   * For events with more than 3 fields use EventLogGroup.registerVarargEvent
   *
   * To implement a new collector:
   * - Record events according to a "fus-collectors.md" dev guide and register it in plugin.xml
   * - Implement custom validation rules if necessary (see [com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator])
   * - If new group is implemented in a platform or a plugin built with IntelliJ Ultimate, YT issue will be created automatically
   * - Otherwise, create a YT issue in FUS project with group data scheme and descriptions to register it on the statistics metadata server
   *
   * To test collector:
   * - If group is not registered on the server, add it to events test scheme with "Add Group to Events Test Scheme" action.
   *   (com.intellij.internal.statistic.actions.scheme.AddGroupToTestSchemeAction)
   *
   * @see registerVarargEvent
   */
  fun registerEvent(eventId: String): EventId {
    return EventId(this, eventId).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String)
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  fun <T1> registerEvent(eventId: String, eventField1: EventField<T1>): EventId1<T1> {
    return EventId1(this, eventId, eventField1).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String)
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  fun <T1, T2> registerEvent(eventId: String, eventField1: EventField<T1>, eventField2: EventField<T2>): EventId2<T1, T2> {
    return EventId2(this, eventId, eventField1, eventField2).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String)
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  fun <T1, T2, T3> registerEvent(eventId: String, eventField1: EventField<T1>, eventField2: EventField<T2>, eventField3: EventField<T3>): EventId3<T1, T2, T3> {
    return EventId3(this, eventId, eventField1, eventField2, eventField3).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String)
   *
   * @see registerEvent
   */
  fun registerVarargEvent(eventId: String, vararg fields: EventField<*>): VarargEventId {
    return VarargEventId(this, eventId, *fields).also { addToRegisteredEvents(it) }
  }

  @JvmOverloads
  fun registerIdeActivity(activityName: String?,
                          startEventAdditionalFields: Array<EventField<*>> = emptyArray(),
                          finishEventAdditionalFields: Array<EventField<*>> = emptyArray(),
                          parentActivity: IdeActivityDefinition? = null): IdeActivityDefinition {
    return IdeActivityDefinition(this, parentActivity, activityName, startEventAdditionalFields, finishEventAdditionalFields)
  }

  internal fun validateEventId(eventId: String) {
    if (!isEventIdValid(eventId)) {
      throw IllegalArgumentException("Trying to report unregistered event ID $eventId to group $id")
    }
  }

  private fun isEventIdValid(eventId: String): Boolean {
    if (EventLogSystemEvents.SYSTEM_EVENTS.contains(eventId)) return true
    return registeredEventIds.isEmpty() || eventId in registeredEventIds
  }
}