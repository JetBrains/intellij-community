// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events.scheme

import org.jetbrains.annotations.ApiStatus

const val FUS_DESCRIPTION_REGISTRATION_ENABLED: String = "FUS_DESCRIPTION_REGISTRATION_ENABLED"

/**
 * Internal processor for managing to store descriptions of event log groups and their events.
 * Provides functionality to store, retrieve descriptions for groups and their corresponding events.
 *
 * Descriptions are registered and stored in the memory if the environment variable [FUS_DESCRIPTION_REGISTRATION_ENABLED] is true.
 * Please use it just for [EventsSchemeBuilderAppStarter] and tests.
 *
 * It ensures that event and group descriptions are immutable once registered.
 * If an attempt is made to override an already registered description with a differing value,
 * an exception is thrown.
 *
 * The processor maintains two internal maps:
 * - A map for group descriptions to associate a description with each event log group.
 * - A nested map for event descriptions within each group.
 */

@ApiStatus.Internal
object RegisteredLogDescriptionsProcessor {
  private val groupDescriptionsMap = HashMap<String, String>()
  private val eventDescriptionsMap = HashMap<String, HashMap<String, String>>()
  private var descriptionRegistrationEnabled = System.getenv(FUS_DESCRIPTION_REGISTRATION_ENABLED)?.toBoolean() ?: false

  /**
   * Resets the descriptions of the current instance by clearing all relevant data.
   */
  fun resetDescriptions() {
    groupDescriptionsMap.clear()
    eventDescriptionsMap.clear()
  }

  /**
   * Enables/disables description registration.
   *
   * @param descriptionRegistrationEnabled A boolean indicating the new registration status to set.
   */
  fun configureDescriptionRegistration(isEnabled: Boolean) {
    this.descriptionRegistrationEnabled = isEnabled
  }

  /**
   * Register a description for a specified group if [descriptionRegistrationEnabled] is true, the description is not empty and not null.
   * Throws an exception if the description for the group already exists and differs from the new one.
   */
  fun registerGroupDescription(groupId: String, description: String?) {
    if (!descriptionRegistrationEnabled || description == null || description.isEmpty()) return

    if (groupDescriptionsMap.containsKey(groupId) && groupDescriptionsMap[groupId] != description) {
      throw IllegalStateException("Trying to override registered event log group description in group '$groupId'. " +
                                  "Old description: ${groupDescriptionsMap[groupId]}, new description: $description.")
    }
    groupDescriptionsMap[groupId] = description
  }

  /**
   * Register a description for an event within a specific group if [descriptionRegistrationEnabled] is true, the description is not empty and not null.
   * Throws an exception if the description for the event already exists and differs from the new one.
   */
  fun registerEventDescription(groupId: String, eventId: String, description: String?) {
    if (!descriptionRegistrationEnabled || description == null || description.isEmpty()) return

    val groupMap = eventDescriptionsMap[groupId]
    if (groupMap == null) {
      eventDescriptionsMap[groupId] = hashMapOf(eventId to description)
      return
    }

    if (!groupMap.containsKey(eventId)) {
      groupMap[eventId] = description
      return
    }

    if (groupMap[eventId] != description) {
      throw IllegalStateException("Trying to override registered event log description for event '$eventId' in group '$groupId'. " +
                                  "Old description: ${groupMap[eventId]}, new description: $description.")
    }
  }

  /**
   * Retrieves the description for a specified group or returns null if [descriptionRegistrationEnabled] is false or the group description is not registered.
   */
  fun calculateGroupDescription(groupId: String): String? {
    if (!descriptionRegistrationEnabled) return null
    return groupDescriptionsMap[groupId]
  }

  /**
   * Retrieves the description for a specific event in the group or returns null if [descriptionRegistrationEnabled] is false or the events description is not registered.
   */
  fun calculateEventDescription(groupId: String, eventId: String): String? {
    if (!descriptionRegistrationEnabled) return null
    val groupMap = eventDescriptionsMap[groupId]
    return groupMap?.get(eventId)
  }
}