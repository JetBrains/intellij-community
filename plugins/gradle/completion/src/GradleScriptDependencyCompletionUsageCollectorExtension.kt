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
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

private const val DESCRIPTOR_NAME = "gradle_script_dep_position"

private val POSITION = EventFields.Enum<GradleScriptDependencyCompletionPosition>(
  DESCRIPTOR_NAME,
  "Position where the dependency completion was invoked in the dependencies block"
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

  override fun getExtensionFields(): List<EventField<*>> = listOf(POSITION)
}

/**
 * Provides additional data for the "finished" event of "completion" FUS group.
 * Any fields reported there should be declared in [GradleScriptDependencyCompletionUsageCollectorExtension].
 */
internal class GradleScriptDependencyCompletionUsageDescriptor : LookupUsageDescriptor {
  override fun getExtensionKey(): String = DESCRIPTOR_NAME

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    val fileName = lookupResultDescriptor.lookup.psiFile?.name ?: return emptyList()
    if (!FileUtilRt.extensionEquals(fileName, "gradle.kts")) return emptyList()

    val item = lookupResultDescriptor.selectedItem
               ?: lookupResultDescriptor.lookup.items.firstOrNull { // if completion was canceled
                 it.getUserData(GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY) != null
               } ?: return emptyList()

    item.getUserData(GRADLE_SCRIPT_DEPENDENCY_COMPLETION_POSITION_KEY)?.let { invokePosition ->
      return listOf(POSITION with invokePosition)
    }

    return emptyList()
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