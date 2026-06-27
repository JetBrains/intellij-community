// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.providers

import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class AgentSessionProviderMenuModel(
  @JvmField val standardItems: List<AgentSessionProviderMenuItem>,
  @JvmField val yoloItems: List<AgentSessionProviderMenuItem>,
)

@ApiStatus.Internal
data class AgentSessionProviderMenuItem(
  @JvmField val bridge: AgentSessionProviderDescriptor,
  @JvmField val mode: AgentSessionLaunchMode,
  @JvmField val labelKey: String,
  @JvmField val isEnabled: Boolean,
  @JvmField val disabledReasonKey: String? = null,
)

@ApiStatus.Internal
fun AgentSessionProviderMenuModel.hasEntries(): Boolean {
  return standardItems.isNotEmpty() || yoloItems.isNotEmpty()
}

/**
 * Sync menu-model builder. Synchronous surfaces (action `update()` callbacks) must supply
 * [availabilityByProvider] from the caller's project-level provider availability cache.
 */
@ApiStatus.Internal
fun buildAgentSessionProviderMenuModel(
  bridges: List<AgentSessionProviderDescriptor>,
  availabilityByProvider: Map<AgentSessionProvider, Boolean>,
): AgentSessionProviderMenuModel {
  val standardItems = ArrayList<AgentSessionProviderMenuItem>(bridges.size)
  val yoloItems = ArrayList<AgentSessionProviderMenuItem>()

  bridges.forEach { bridge ->
    val cliAvailable = availabilityByProvider[bridge.provider] == true
    appendMenuItems(bridge, cliAvailable, standardItems, yoloItems)
  }

  return AgentSessionProviderMenuModel(
    standardItems = standardItems,
    yoloItems = yoloItems,
  )
}

private fun appendMenuItems(
  bridge: AgentSessionProviderDescriptor,
  cliAvailable: Boolean,
  standardItems: MutableList<AgentSessionProviderMenuItem>,
  yoloItems: MutableList<AgentSessionProviderMenuItem>,
) {
  if (!cliAvailable && bridge.cliVisibilityPolicy == AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE) {
    return
  }

  val disabledReasonKey = if (cliAvailable) null else bridge.cliMissingMessageKey

  if (AgentSessionLaunchMode.STANDARD in bridge.supportedLaunchModes) {
    standardItems += AgentSessionProviderMenuItem(
      bridge = bridge,
      mode = AgentSessionLaunchMode.STANDARD,
      labelKey = bridge.newSessionLabelKey,
      isEnabled = cliAvailable,
      disabledReasonKey = disabledReasonKey,
    )
  }

  val yoloLabelKey = bridge.yoloSessionLabelKey
  if (yoloLabelKey != null && AgentSessionLaunchMode.YOLO in bridge.supportedLaunchModes) {
    yoloItems += AgentSessionProviderMenuItem(
      bridge = bridge,
      mode = AgentSessionLaunchMode.YOLO,
      labelKey = yoloLabelKey,
      isEnabled = cliAvailable,
      disabledReasonKey = disabledReasonKey,
    )
  }
}
