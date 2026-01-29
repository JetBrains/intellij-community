// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

private val IS_AUTO_POPUP = EventFields.Boolean(
  "gradle_deps_is_auto_popup",
  "True if the completion was triggered by auto popup, false if it was invoked manually"
)

private val PROVIDER_TYPE = EventFields.Enum<DependenciesCompletionProviderType>(
  "gradle_deps_provider_type",
  "Type of the dependency completion provider"
)

private val INVOKE_POSITION = EventFields.Enum<DependenciesCompletionInvokePosition>(
  "gradle_deps_invoke_position",
  "Position where the completion was invoked in the dependencies block"
)

@ApiStatus.Internal
val GRADLE_COMPLETION_IS_AUTO_POPUP: Key<Boolean> = Key.create("GRADLE_COMPLETION_IS_AUTO_POPUP")

@ApiStatus.Internal
val GRADLE_COMPLETION_PROVIDER_TYPE: Key<DependenciesCompletionProviderType> = Key.create("GRADLE_COMPLETION_PROVIDER_TYPE")

@ApiStatus.Internal
val GRADLE_COMPLETION_INVOKE_POSITION: Key<DependenciesCompletionInvokePosition> = Key.create("GRADLE_COMPLETION_INVOKE_POSITION")

internal class GradleDependencyCompletionUsageCollectorExtension : FeatureUsageCollectorExtension {
  override fun getGroupId(): @NonNls String = LookupUsageTracker.GROUP_ID

  override fun getEventId(): String = LookupUsageTracker.FINISHED_EVENT_ID

  override fun getExtensionFields(): List<EventField<*>> {
    return listOf(
      IS_AUTO_POPUP,
      PROVIDER_TYPE,
      INVOKE_POSITION,
    )
  }
}

internal class GradleDependencyCompletionUsageDescriptor : LookupUsageDescriptor {
  override fun getExtensionKey(): String = "gradle_deps"

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    val selectedItem = lookupResultDescriptor.selectedItem ?: return emptyList()
    val isGradleDependency = selectedItem.getUserData(GRADLE_DEPENDENCY_COMPLETION) ?: return emptyList()
    if (!isGradleDependency) return emptyList()

    val result = mutableListOf<EventPair<*>>()

    selectedItem.getUserData(GRADLE_COMPLETION_IS_AUTO_POPUP)?.let { isAutoPopup ->
      result.add(IS_AUTO_POPUP with isAutoPopup)
    }

    selectedItem.getUserData(GRADLE_COMPLETION_PROVIDER_TYPE)?.let { providerType ->
      result.add(PROVIDER_TYPE with providerType)
    }

    selectedItem.getUserData(GRADLE_COMPLETION_INVOKE_POSITION)?.let { invokePosition ->
      result.add(INVOKE_POSITION with invokePosition)
    }

    return result
  }
}

@ApiStatus.Internal
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

@ApiStatus.Internal
enum class DependenciesCompletionProviderType {
  LOCAL, SERVER, SERVER_CACHE
}