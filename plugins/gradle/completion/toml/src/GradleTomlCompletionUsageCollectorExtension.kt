// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental

package com.intellij.gradle.completion.toml

import com.intellij.codeInsight.lookup.impl.LookupResultDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageTracker
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.toml.lang.TomlLanguage

private const val DESCRIPTOR_NAME = "gradle_toml_lib_position"

private val POSITION = EventFields.Enum<GradleTomlLibraryCompletionPosition>(
  DESCRIPTOR_NAME,
  "Position where the library completion was invoked in the libraries table"
)

val GRADLE_TOML_LIBRARY_COMPLETION_POSITION_KEY: Key<GradleTomlLibraryCompletionPosition> =
  Key.create("GRADLE_TOML_LIBRARY_COMPLETION_POSITION")

/**
 * Declares additional fields that can be reported with the "finished" event of "completion" FUS group.
 * Version of [LookupUsageTracker.GROUP] should be incremented every time any field is changed there.
 */
internal class GradleTomlCompletionUsageCollectorExtension : FeatureUsageCollectorExtension {
  override fun getGroupId(): @NonNls String = LookupUsageTracker.GROUP_ID

  override fun getEventId(): String = LookupUsageTracker.FINISHED_EVENT_ID

  override fun getExtensionFields(): List<EventField<*>> = listOf(POSITION)
}

/**
 * Provides additional data for the "finished" event of "completion" FUS group.
 * Any fields reported there should be declared in [GradleTomlCompletionUsageCollectorExtension].
 */
internal class GradleTomlCompletionUsageDescriptor : LookupUsageDescriptor {
  override fun getExtensionKey(): String = DESCRIPTOR_NAME

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    if (lookupResultDescriptor.language != TomlLanguage) return emptyList()

    val item = lookupResultDescriptor.selectedItem
               ?: lookupResultDescriptor.lookup.items.firstOrNull { // if completion was canceled
                 it.getUserData(GRADLE_TOML_LIBRARY_COMPLETION_POSITION_KEY) != null
               } ?: return emptyList()

    item.getUserData(GRADLE_TOML_LIBRARY_COMPLETION_POSITION_KEY)?.let { invokePosition ->
      return listOf(POSITION with invokePosition)
    }

    return emptyList()
  }
}

enum class GradleTomlLibraryCompletionPosition {
  /**
   * Single GAV string: `junit = "juni<caret>"`
   */
  GAV,

  /**
   * Module string: `junit.module = "juni<caret>"`
   */
  MODULE,

  /**
   * Group parameter: `junit = { group = "juni<caret>" }`
   */
  GROUP,

  /**
   * Name parameter: `junit = { name = "juni<caret>" }`
   */
  ARTIFACT,

  /**
   * Version parameter: `junit = { version = "5<caret>" }`
   */
  VERSION
}