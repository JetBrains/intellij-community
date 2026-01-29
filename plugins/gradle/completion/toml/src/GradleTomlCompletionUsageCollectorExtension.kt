// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental

package com.intellij.gradle.toml

import com.intellij.codeInsight.lookup.impl.LookupResultDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageTracker
import com.intellij.gradle.completion.GRADLE_DEPENDENCY_COMPLETION
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

private val POSITION = EventFields.Enum<GradleTomlCompletionPosition>(
  "gradle_toml_position",
  "Position where the completion was invoked in the libraries table"
)

val GRADLE_TOML_COMPLETION_POSITION_KEY: Key<GradleTomlCompletionPosition> = Key.create("GRADLE_TOML_COMPLETION_POSITION")

/**
 * Declares additional fields that can be reported with the "finished" event of "completion" FUS group.
 * Version of [LookupUsageTracker.GROUP] should be incremented every time any field is changed there.
 */
internal class GradleTomlCompletionUsageCollectorExtension : FeatureUsageCollectorExtension {
  override fun getGroupId(): @NonNls String = LookupUsageTracker.GROUP_ID

  override fun getEventId(): String = LookupUsageTracker.FINISHED_EVENT_ID

  override fun getExtensionFields(): List<EventField<*>> {
    return listOf(POSITION)
  }
}

/**
 * Provides additional data for the "finished" event of "completion" FUS group.
 * Any fields reported there should be declared in [GradleTomlCompletionUsageCollectorExtension].
 */
internal class GradleTomlCompletionUsageDescriptor : LookupUsageDescriptor {
  override fun getExtensionKey(): String = "gradle_toml"

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    val selectedItem = lookupResultDescriptor.selectedItem ?: return emptyList()
    val isGradleDependency = selectedItem.getUserData(GRADLE_DEPENDENCY_COMPLETION) ?: return emptyList()
    if (!isGradleDependency) return emptyList()

    val result = mutableListOf<EventPair<*>>()

    selectedItem.getUserData(GRADLE_TOML_COMPLETION_POSITION_KEY)?.let { invokePosition ->
      result.add(POSITION with invokePosition)
    }

    return result
  }
}

enum class GradleTomlCompletionPosition {
  /**
   * Single GAV string: `junit = "juni<caret>"`
   */
  GAV,

  /**
   * Module string: `junit.module = "juni<caret>"`
   */
  MODULE,

  /**
   * Group parameter: `junit = { group = "juni<caret>"}`
   */
  GROUP,

  /**
   * Name parameter: `junit = { name = "juni<caret>"}`
   */
  ARTIFACT,

  /**
   * Version parameter: `junit = { version = "juni<caret>"}`
   */
  VERSION
}