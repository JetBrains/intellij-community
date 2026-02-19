// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object ComposeResourcesUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("compose.resources", 1)

  override fun getGroup(): EventLogGroup? = GROUP

  private val ActionTypeField = EventFields.NullableEnum<ActionType>("action_type")
  private val ResourceBaseTypeField = EventFields.NullableEnum<ResourceBaseType>("resource_base_type")
  private val ResourceTypeField = EventFields.NullableEnum<ResourceType>("resource_type")
  private val ElementCountField = EventFields.Int("element_count")

  private val COMPOSE_RESOURCES_ACTION = GROUP.registerVarargEvent(
    "action.invoked",
    ActionTypeField,
    ResourceBaseTypeField,
    ResourceTypeField,
    ElementCountField,
  )

  fun logAction(actionType: ActionType, resourceBaseType: ResourceBaseType?, resourceType: ResourceType?, elementCount: Int = -1) {
    COMPOSE_RESOURCES_ACTION.log(
      ActionTypeField with actionType,
      ResourceBaseTypeField with resourceBaseType,
      ResourceTypeField with resourceType,
      ElementCountField with elementCount,
    )
  }

  internal enum class ActionType {
    NAVIGATE,
    RENAME,
    FIND_USAGES,
  }

  internal enum class ResourceBaseType { STRING, FILE }
}
