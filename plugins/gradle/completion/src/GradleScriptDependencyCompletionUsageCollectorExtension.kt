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

private val POSITION = EventFields.Enum<GradleScriptDependencyCompletionPosition>(
  "gradle_script_deps_position",
  "Position where the completion was invoked in the dependencies block"
)

val GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY: Key<GradleScriptDependencyCompletionPosition> =
  Key.create("GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION")

/**
 * Declares additional fields that can be reported with the "finished" event of "completion" FUS group.
 * Version of [LookupUsageTracker.GROUP] should be incremented every time any field is changed there.
 */
internal class GradleScriptDependencyCompletionUsageCollectorExtension : FeatureUsageCollectorExtension {
  override fun getGroupId(): @NonNls String = LookupUsageTracker.GROUP_ID

  override fun getEventId(): String = LookupUsageTracker.FINISHED_EVENT_ID

  override fun getExtensionFields(): List<EventField<*>> {
    return listOf(POSITION)
  }
}

/**
 * Provides additional data for the "finished" event of "completion" FUS group.
 * Any fields reported there should be declared in [GradleScriptDependencyCompletionUsageCollectorExtension].
 */
internal class GradleScriptDependencyCompletionUsageDescriptor : LookupUsageDescriptor {
  override fun getExtensionKey(): String = "gradle_script_deps"

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    val selectedItem = lookupResultDescriptor.selectedItem ?: return emptyList()
    val isGradleDependency = selectedItem.getUserData(GRADLE_DEPENDENCY_COMPLETION) ?: return emptyList()
    if (!isGradleDependency) return emptyList()

    val result = mutableListOf<EventPair<*>>()

    selectedItem.getUserData(GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY)?.let { invokePosition ->
      result.add(POSITION with invokePosition)
    }

    return result
  }
}

enum class GradleScriptDependencyCompletionPosition {
  /**
   * Top-level in dependencies block: `dependencies { juni<caret> }`
   */
  TOP_LEVEL,

  /**
   * Single GAV string: `implementation("juni<caret>")`
   */
  GAV,

  /**
   * Named or positional group parameter: `implementation(group = "juni<caret>")`
   */
  GROUP,

  /**
   * Named or positional artifact parameter: `implementation(name = "juni<caret>")`
   */
  ARTIFACT,

  /**
   * Named or positional version parameter: `implementation(version = "5<caret>")`
   */
  VERSION,

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