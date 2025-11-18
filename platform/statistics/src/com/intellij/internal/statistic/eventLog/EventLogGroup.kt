// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.events.scheme.FUS_DESCRIPTION_REGISTRATION_ENABLED
import com.intellij.internal.statistic.eventLog.events.scheme.RegisteredLogDescriptionsProcessor
import org.jetbrains.annotations.NonNls

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
  @NonNls
  @EventIdName
  val id: String
  val version: Int
  val recorder: String
  val groupData: List<Pair<EventField<*>, FeatureUsageData.() -> Unit>>

  /**
   * Constructs an EventLogGroup.
   *
   * @param id The unique identifier for the event group.
   * @param version The version of the event group.
   * @param recorder The recorder that logs events from this group.
   * @param description A textual description of the event group.
   * The description is not null and not an empty string.
   * The description is registered at event group initialization using the RegisteredLogDescriptionsProcessor.
   * There is no description in the memory if the environment variable [FUS_DESCRIPTION_REGISTRATION_ENABLED] is false.
   * Descriptions are stored in memory just for [com.intellij.internal.statistic.eventLog.events.scheme.EventsSchemeBuilderAppStarter] and unit tests.
   * @param groupData EventFields in groupData are going to be appended to every event in the group. To provide the data, a supplier
   * function is passed along with each EventField. See [com.intellij.internal.statistic.eventLog.events.EventId] for appending logic.
   *
   * @throws IllegalArgumentException If the description is empty.
   */
  constructor(
    @NonNls @EventIdName id: String,
    version: Int,
    recorder: String = FUS_RECORDER,
    description: String,
    groupData: List<Pair<EventField<*>, FeatureUsageData.() -> Unit>> = emptyList(),
  ) {
    if (description.isEmpty()) {
      throw IllegalArgumentException("Recorder $recorder, group ID $id: the group description can't be empty string.")
    }
    this.id = id
    this.version = version
    this.recorder = recorder
    this.groupData = groupData
    RegisteredLogDescriptionsProcessor.registerGroupDescription(id, description)
  }

  // for binary compatibility
  @Deprecated("Please switch to constructor where group description is provided. " +
              "Current description can be found in metadata repository or in Data Office Web portal.",
              ReplaceWith("EventLogGroup(id, version, recorder, TODO(\"provide group description\"))"))
  @JvmOverloads
  constructor(
    @NonNls @EventIdName id: String,
    version: Int,
    recorder: String = FUS_RECORDER,
  ) {
    this.id = id
    this.version = version
    this.recorder = recorder
    this.groupData = emptyList()
  }

  // for binary compatibility
  @Deprecated("Please switch to constructor where group description is provided. " +
              "Current description can be found in metadata repository or in Data Office Web portal.",
              ReplaceWith("EventLogGroup(id, version, recorder, \"TODO: provide group description\")"))
  constructor(
    @NonNls @EventIdName id: String,
    version: Int,
    recorder: String = FUS_RECORDER,
    description: String?,
  ) : this(id, version, recorder) {
    RegisteredLogDescriptionsProcessor.registerGroupDescription(id, description)
  }

  private val registeredEventIds = mutableSetOf<String>()
  private val registeredEvents = mutableListOf<BaseEventId>()

  val events: List<BaseEventId> get() = registeredEvents

  private fun addToRegisteredEvents(eventId: BaseEventId) {
    registeredEvents.add(eventId)
    registeredEventIds.add(eventId.eventId)
  }

  @Deprecated("Please switch to method where event description is provided. " +
              "Current description can be found in metadata repository or in Data Office Web portal.",
              ReplaceWith("registerEvent(eventId, TODO(\"provide event description\"))"))
  fun registerEvent(@NonNls @EventIdName eventId: String): EventId {
    return EventId(this, eventId, null).also { addToRegisteredEvents(it) }
  }

  /**
   * New style API to record IDE events (e.g., invoked action, opened dialog) or state.
   *
   * @param description The unique identifier for the event.
   * The description is not null and not an empty string.
   * The description is registered at event initialization using the [RegisteredLogDescriptionsProcessor].
   * There is no description in the memory if the environment variable [FUS_DESCRIPTION_REGISTRATION_ENABLED] is false.
   * Descriptions are stored in memory just for [com.intellij.internal.statistic.eventLog.events.scheme.EventsSchemeBuilderAppStarter] and unit tests.
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
  fun registerEvent(@NonNls @EventIdName eventId: String, @NonNls description: String): EventId {
    return EventId(this, eventId, description).also { addToRegisteredEvents(it) }
  }

  @Deprecated("Please switch to method where event description is provided. " +
              "Current description can be found in metadata repository or in Data Office Web portal.",
              ReplaceWith("registerEvent(eventId, eventField1, TODO(\"provide event description\"))"))
  fun <T1> registerEvent(
    @NonNls @EventIdName eventId: String,
    eventField1: EventField<T1>
  ): EventId1<T1> {
    return EventId1(this, eventId, null, eventField1).also { addToRegisteredEvents(it) }
  }

  @Deprecated("This method was added for compatibility with Bazel.",
              ReplaceWith("registerEvent(eventId, eventField1, TODO(\"provide event description\"))"))
  fun <T1> registerEvent(
    @NonNls @EventIdName eventId: String,
    eventField1: EventField<T1>,
    description: String,
    intValue: Int,
    objectValue: Object
  ): EventId1<T1> {
    return EventId1(this, eventId, description, eventField1).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String, java.lang.String)
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  fun <T1> registerEvent(
    @NonNls @EventIdName eventId: String,
    eventField1: EventField<T1>,
    @NonNls description: String
  ): EventId1<T1> {
    return EventId1(this, eventId, description, eventField1).also { addToRegisteredEvents(it) }
  }

  @Deprecated("Please switch to method where event description is provided. " +
              "Current description can be found in metadata repository or in Data Office Web portal.",
              ReplaceWith("registerEvent(eventId, eventField1, eventField2, TODO(\"provide event description\"))"))
  fun <T1, T2> registerEvent(
    @NonNls @EventIdName eventId: String,
    eventField1: EventField<T1>,
    eventField2: EventField<T2>
  ): EventId2<T1, T2> {
    return EventId2(this, eventId, null, eventField1, eventField2).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String, java.lang.String)
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  fun <T1, T2> registerEvent(
    @NonNls @EventIdName eventId: String,
    eventField1: EventField<T1>,
    eventField2: EventField<T2>,
    @NonNls description: String
  ): EventId2<T1, T2> {
    return EventId2(this, eventId, description, eventField1, eventField2).also { addToRegisteredEvents(it) }
  }

  @Deprecated("Please switch to method where event description is provided. " +
              "Current description can be found in metadata repository or in Data Office Web portal.",
              ReplaceWith("registerEvent(eventId, eventField1, eventField2, eventField3, TODO(\"provide event description\"))"))
  fun <T1, T2, T3> registerEvent(
    @NonNls @EventIdName eventId: String,
    eventField1: EventField<T1>,
    eventField2: EventField<T2>,
    eventField3: EventField<T3>
  ): EventId3<T1, T2, T3> {
    return EventId3(this, eventId, null, eventField1, eventField2, eventField3).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String, java.lang.String)
   *
   * @see registerEvent
   * @see registerVarargEvent
   */
  fun <T1, T2, T3> registerEvent(
    @NonNls @EventIdName eventId: String,
    eventField1: EventField<T1>,
    eventField2: EventField<T2>,
    eventField3: EventField<T3>,
    @NonNls description: String
  ): EventId3<T1, T2, T3> {
    return EventId3(this, eventId, description, eventField1, eventField2, eventField3).also { addToRegisteredEvents(it) }
  }

  @Deprecated("Please switch to method where event description is provided. " +
              "Current description can be found in metadata repository or in Data Office Web portal.",
              ReplaceWith("registerVarargEvent(eventId, TODO(\"provide event description\"), fields)"))
  fun registerVarargEvent(@NonNls @EventIdName eventId: String, vararg fields: EventField<*>): VarargEventId {
    return VarargEventId(this, eventId, null, *fields).also { addToRegisteredEvents(it) }
  }

  /**
   * See docs for com.intellij.internal.statistic.eventLog.EventLogGroup.registerEvent(java.lang.String, java.lang.String)
   *
   * @see registerEvent
   */
  fun registerVarargEvent(@NonNls @EventIdName eventId: String, @NonNls description: String, vararg fields: EventField<*>): VarargEventId {
    return VarargEventId(this, eventId, description, *fields).also { addToRegisteredEvents(it) }
  }

  @JvmOverloads
  fun registerIdeActivity(
    @NonNls @EventIdName activityName: String?,
    startEventAdditionalFields: Array<EventField<*>> = emptyArray(),
    finishEventAdditionalFields: Array<EventField<*>> = emptyArray(),
    parentActivity: IdeActivityDefinition? = null,
    subStepWithStepId: Boolean = false,
  ): IdeActivityDefinition {
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