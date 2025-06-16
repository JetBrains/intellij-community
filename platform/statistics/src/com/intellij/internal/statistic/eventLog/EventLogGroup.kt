// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.eventLog.events.*
import org.jetbrains.annotations.NonNls

/**
 * Represents a group of events used for feature usage statistics.
 *
 * This class is responsible for registering events within a specific group context.
 *
 * Best practices:
 *  - Prefer a bigger group with many (related) event types to many small groups of 1-2 events each
 *  - Prefer shorter group names; avoid common prefixes (such as "statistics.")
 *
 * @property id The unique identifier for this group of events.
 * @property version The version of the event group. Has to be incremented when changes are made to the group and/or events
 * @property recorder The recorder name associated with this event group.
 * @property description A description of the event group.
 * @property groupData EventFields in groupData are going to be appended to every event in the group. To provide the data, a supplier
 *  function is passed along with each EventField. See [com.intellij.internal.statistic.eventLog.events.EventId] for appending logic.
 */
open class EventLogGroup(@NonNls @EventIdName val id: String,
                    val version: Int,
                    val recorder: String,
                    val description: String?,
                    val groupData: List<Pair<EventField<*>, FeatureUsageData.() -> Unit>> = emptyList()) {
  // for binary compatibility
  @JvmOverloads
  constructor(@NonNls @EventIdName id: String,
              version: Int,
              recorder: String = "FUS") : this(id, version, recorder, null, emptyList())

  // for binary compatibility
  constructor(@NonNls @EventIdName id: String,
              version: Int,
              recorder: String,
              description: String?) : this(id, version, recorder, description, emptyList())

  private val registeredEventIds = mutableSetOf<String>()
  private val registeredEvents = mutableListOf<BaseEventId>()

  val events: List<BaseEventId> get() = registeredEvents

  private fun addToRegisteredEvents(eventId: BaseEventId) {
    registeredEvents.add(eventId)
    registeredEventIds.add(eventId.eventId)
  }

  /**
   * New style API to record IDE events (e.g., invoked action, opened dialog) or state.
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
   * - If a group is not registered on the server, add it to an events test scheme with "Add Group to Events Test Scheme" action.
   *   (com.intellij.internal.statistic.actions.scheme.AddGroupToTestSchemeAction)
   *
   * @see registerVarargEvent
   */
  @JvmOverloads
  fun registerEvent(@NonNls @EventIdName eventId: String, @NonNls description: String? = null): EventId {
    return EventId(this, eventId, description).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String)
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  @JvmOverloads
  fun <T1> registerEvent(@NonNls @EventIdName eventId: String,
                         eventField1: EventField<T1>,
                         @NonNls description: String? = null): EventId1<T1> {
    return EventId1(this, eventId, description, eventField1).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String)
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  @JvmOverloads
  fun <T1, T2> registerEvent(@NonNls @EventIdName eventId: String,
                             eventField1: EventField<T1>,
                             eventField2: EventField<T2>,
                             @NonNls description: String? = null): EventId2<T1, T2> {
    return EventId2(this, eventId, description, eventField1, eventField2).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String)
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  @JvmOverloads
  fun <T1, T2, T3> registerEvent(@NonNls @EventIdName eventId: String,
                                 eventField1: EventField<T1>,
                                 eventField2: EventField<T2>,
                                 eventField3: EventField<T3>,
                                 @NonNls description: String? = null): EventId3<T1, T2, T3> {
    return EventId3(this, eventId, description, eventField1, eventField2, eventField3).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String)
   *
   * @see registerEvent
   */
  fun registerVarargEvent(@NonNls @EventIdName eventId: String, vararg fields: EventField<*>): VarargEventId {
    return VarargEventId(this, eventId, null, *fields).also { addToRegisteredEvents(it) }
  }

  fun registerVarargEvent(@NonNls @EventIdName eventId: String, @NonNls description: String? = null, vararg fields: EventField<*>): VarargEventId {
    return VarargEventId(this, eventId, description, *fields).also { addToRegisteredEvents(it) }
  }

  @JvmOverloads
  fun registerIdeActivity(@NonNls @EventIdName activityName: String?,
                          startEventAdditionalFields: Array<EventField<*>> = emptyArray(),
                          finishEventAdditionalFields: Array<EventField<*>> = emptyArray(),
                          parentActivity: IdeActivityDefinition? = null,
                          subStepWithStepId: Boolean = false): IdeActivityDefinition {
    return IdeActivityDefinition(this, parentActivity, activityName, startEventAdditionalFields, finishEventAdditionalFields, subStepWithStepId)
  }

  internal fun validateEventId(@NonNls @EventIdName eventId: String) {
    if (!isEventIdValid(eventId)) {
      throw IllegalArgumentException("Trying to report unregistered event ID $eventId to group $id")
    }
  }

  private fun isEventIdValid(@NonNls @EventIdName eventId: String): Boolean {
    if (EventLogSystemEvents.SYSTEM_EVENTS.contains(eventId)) return true
    return registeredEventIds.isEmpty() || eventId in registeredEventIds
  }

  override fun toString(): String {
    return "EventLogGroup(id='$id')"
  }
}