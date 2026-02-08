// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.eventLog.events.BaseEventId
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.eventLog.events.EventIdName
import com.intellij.internal.statistic.eventLog.events.VarargEventId

const val FUS_RECORDER: String = "FUS"

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
 * @property groupData EventFields in groupData are going to be appended to every event in the group. To provide the data, a supplier
 *  function is passed along with each EventField. See [com.intellij.internal.statistic.eventLog.events.EventId] for appending logic.
 */
open class EventLogGroup {
  @EventIdName val id: String
  val version: Int
  val recorder: String
  val groupData: List<Pair<EventField<*>, FeatureUsageData.() -> Unit>>

  @Deprecated("Descriptions are moved to a separate file; use another constructor", ReplaceWith("EventLogGroup(id, version, recorder, groupData)"))
  constructor(
    @EventIdName id: String,
    version: Int,
    recorder: String = FUS_RECORDER,
    @Suppress("unused") description: String,
    groupData: List<Pair<EventField<*>, FeatureUsageData.() -> Unit>> = emptyList(),
  ) : this(id, version, recorder, groupData)

  /**
   * Constructs an EventLogGroup.
   *
   * @param id The unique identifier for the event group.
   * @param version The version of the event group.
   * @param recorder The recorder that logs events from this group.
   * @param groupData EventFields in groupData are going to be appended to every event in the group. To provide the data, a supplier
   * function is passed along with each EventField. See [com.intellij.internal.statistic.eventLog.events.EventId] for appending logic.
   *
   * @throws IllegalArgumentException If the description is empty.
   */
  @JvmOverloads
  constructor(
    @EventIdName id: String,
    version: Int,
    recorder: String = FUS_RECORDER,
    groupData: List<Pair<EventField<*>, FeatureUsageData.() -> Unit>> = emptyList(),
  ) {
    this.id = id
    this.version = version
    this.recorder = recorder
    this.groupData = groupData
  }

  @Deprecated("Descriptions are moved to a separate file; use another constructor", ReplaceWith("EventLogGroup(id, version, recorder)"))
  constructor(
    @EventIdName id: String,
    version: Int,
    recorder: String = FUS_RECORDER,
    @Suppress("unused") description: String?,
  ) : this(id, version, recorder)

  private val registeredEventIds = mutableSetOf<String>()
  private val registeredEvents = mutableListOf<BaseEventId>()

  val events: List<BaseEventId> get() = registeredEvents

  private fun addToRegisteredEvents(eventId: BaseEventId) {
    registeredEvents.add(eventId)
    registeredEventIds.add(eventId.eventId)
  }

  @Deprecated("Descriptions are moved to a separate file; inline the usage")
  fun registerEvent(@EventIdName eventId: String, @Suppress("unused") description: String): EventId = registerEvent(eventId)

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
  fun registerEvent(@EventIdName eventId: String): EventId {
    return EventId(this, eventId).also { addToRegisteredEvents(it) }
  }

  @Deprecated("Descriptions are moved to a separate file; inline the usage")
  fun <T1> registerEvent(
    @EventIdName eventId: String,
    eventField1: EventField<T1>,
    @Suppress("unused") description: String
  ): EventId1<T1> = registerEvent(eventId, eventField1)

  @Deprecated("This method was added for compatibility with Bazel.", ReplaceWith("registerEvent(eventId, eventField1)"))
  fun <T1> registerEvent(
    @EventIdName eventId: String,
    eventField1: EventField<T1>,
    @Suppress("unused") description: String,
    @Suppress("unused") intValue: Int,
    @Suppress("unused", "PLATFORM_CLASS_MAPPED_TO_KOTLIN") objectValue: Object
  ): EventId1<T1> = registerEvent(eventId, eventField1)

  /**
   * See docs for `EventLogGroup.registerEvent(String)`.
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  fun <T1> registerEvent(
    @EventIdName eventId: String,
    eventField1: EventField<T1>,
  ): EventId1<T1> {
    return EventId1(this, eventId, eventField1).also { addToRegisteredEvents(it) }
  }

  @Deprecated("Descriptions are moved to a separate file; inline the usage")
  fun <T1, T2> registerEvent(
    @EventIdName eventId: String,
    eventField1: EventField<T1>,
    eventField2: EventField<T2>,
    @Suppress("unused") description: String
  ): EventId2<T1, T2> = registerEvent(eventId, eventField1, eventField2)

  /**
   * See docs for `EventLogGroup.registerEvent(String)`.
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  fun <T1, T2> registerEvent(
    @EventIdName eventId: String,
    eventField1: EventField<T1>,
    eventField2: EventField<T2>
  ): EventId2<T1, T2> {
    return EventId2(this, eventId, eventField1, eventField2).also { addToRegisteredEvents(it) }
  }

  @Deprecated("Descriptions are moved to a separate file; inline the usage")
  fun <T1, T2, T3> registerEvent(
    @EventIdName eventId: String,
    eventField1: EventField<T1>,
    eventField2: EventField<T2>,
    eventField3: EventField<T3>,
    @Suppress("unused") description: String
  ): EventId3<T1, T2, T3> = registerEvent(eventId, eventField1, eventField2, eventField3)

  /**
   * See docs for `EventLogGroup.registerEvent(String)`.
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  fun <T1, T2, T3> registerEvent(
    @EventIdName eventId: String,
    eventField1: EventField<T1>,
    eventField2: EventField<T2>,
    eventField3: EventField<T3>
  ): EventId3<T1, T2, T3> {
    return EventId3(this, eventId, eventField1, eventField2, eventField3).also { addToRegisteredEvents(it) }
  }

  @Deprecated("Descriptions are moved to a separate file; inline the usage")
  fun registerVarargEvent(@EventIdName eventId: String, @Suppress("unused") description: String, vararg fields: EventField<*>): VarargEventId =
    registerVarargEvent(eventId, *fields)

  /**
   * See docs for `EventLogGroup.registerEvent(String)`.
   *
   * @see registerEvent
   */
  fun registerVarargEvent(@EventIdName eventId: String, vararg fields: EventField<*>): VarargEventId {
    return VarargEventId(this, eventId, *fields).also { addToRegisteredEvents(it) }
  }

  @JvmOverloads
  fun registerIdeActivity(
    @EventIdName activityName: String?,
    startEventAdditionalFields: Array<EventField<*>> = emptyArray(),
    finishEventAdditionalFields: Array<EventField<*>> = emptyArray(),
    parentActivity: IdeActivityDefinition? = null,
    subStepWithStepId: Boolean = false,
  ): IdeActivityDefinition {
    return IdeActivityDefinition(this, parentActivity, activityName, startEventAdditionalFields, finishEventAdditionalFields, subStepWithStepId)
  }

  internal fun validateEventId(@EventIdName eventId: String) {
    if (!isEventIdValid(eventId)) {
      throw IllegalArgumentException("Trying to report unregistered event ID $eventId to group $id")
    }
  }

  private fun isEventIdValid(@EventIdName eventId: String): Boolean {
    return eventId in EventLogSystemEvents.SYSTEM_EVENTS || registeredEventIds.isEmpty() || eventId in registeredEventIds
  }

  override fun toString(): String = "EventLogGroup(id='$id')"
}
