// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental

package com.intellij.gradle.completion

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

private val INVOKE_POSITION = EventFields.Enum<DependenciesCompletionInvokePosition>(
  "gradle_deps_invoke_position",
  "Position where the completion was invoked in the dependencies block"
)

val GRADLE_COMPLETION_INVOKE_POSITION: Key<DependenciesCompletionInvokePosition> = Key.create("GRADLE_COMPLETION_INVOKE_POSITION")

/**
 * Declares additional fields that can be reported with the "finished" event of "completion" FUS group.
 * Version of [LookupUsageTracker.GROUP] should be incremented every time any field is changed there.
 */
internal class GradleDependencyCompletionUsageCollectorExtension : FeatureUsageCollectorExtension {
  override fun getGroupId(): @NonNls String = LookupUsageTracker.GROUP_ID

  override fun getEventId(): String = LookupUsageTracker.FINISHED_EVENT_ID

  override fun getExtensionFields(): List<EventField<*>> {
    return listOf(INVOKE_POSITION)
  }
}

/**
 * Provides additional data for the "finished" event of "completion" FUS group.
 * Any fields reported there should be declared in [GradleDependencyCompletionUsageCollectorExtension].
 */
internal class GradleDependencyCompletionUsageDescriptor : LookupUsageDescriptor {
  override fun getExtensionKey(): String = "gradle_deps"

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    val selectedItem = lookupResultDescriptor.selectedItem ?: return emptyList()
    val isGradleDependency = selectedItem.getUserData(GRADLE_DEPENDENCY_COMPLETION) ?: return emptyList()
    if (!isGradleDependency) return emptyList()

    val result = mutableListOf<EventPair<*>>()

    selectedItem.getUserData(GRADLE_COMPLETION_INVOKE_POSITION)?.let { invokePosition ->
      result.add(INVOKE_POSITION with invokePosition)
    }

    return result
  }
}

enum class DependenciesCompletionInvokePosition {
  /**
   * Top-level in dependencies block: `dependencies { juni<caret> }`
   */
  TOP_LEVEL,

  /**
   * Single GAV string: `implementation("juni<caret>")`
   */
  GAV,

  /**
   * Named group parameter: `implementation(group = "juni<caret>")`
   */
  NAMED_GROUP,

  /**
   * Named artifact parameter: `implementation(name = "juni<caret>")`
   */
  NAMED_ARTIFACT,

  /**
   * Named version parameter: `implementation(version = "5<caret>")`
   */
  NAMED_VERSION,

  /**
   * Positional group parameter: `implementation("juni<caret>", "junit")`
   */
  POSITIONAL_GROUP,

  /**
   * Positional artifact parameter: `implementation("junit", "juni<caret>")`
   */
  POSITIONAL_ARTIFACT,

  /**
   * Positional version parameter: `implementation("junit", "junit", "5<caret>")`
   */
  POSITIONAL_VERSION,

  /**
   * Exclude group: `implementation(...) { exclude(group = "juni<caret>") }`
   */
  EXCLUDE_GROUP,

  /**
   * Exclude module: `implementation(...) { exclude(module = "juni<caret>") }`
   */
  EXCLUDE_MODULE,

  /**
   * Other argument position
   */
  OTHER
}