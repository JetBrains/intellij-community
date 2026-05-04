// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider

data class AgentSessionProviderMenuModel(
  @JvmField val standardItems: List<AgentSessionProviderMenuItem>,
  @JvmField val yoloItems: List<AgentSessionProviderMenuItem>,
)

data class AgentSessionProviderActionModel(
  @JvmField val menuModel: AgentSessionProviderMenuModel,
  @JvmField val quickStartItem: AgentSessionProviderMenuItem?,
)

data class AgentSessionProviderMenuItem(
    @JvmField val bridge: AgentSessionProviderDescriptor,
    @JvmField val mode: AgentSessionLaunchMode,
    @JvmField val labelKey: String,
    @JvmField val isEnabled: Boolean,
    @JvmField val disabledReasonKey: String? = null,
)

fun AgentSessionProviderMenuModel.hasEntries(): Boolean {
  return standardItems.isNotEmpty() || yoloItems.isNotEmpty()
}

fun buildAgentSessionProviderMenuModel(bridges: List<AgentSessionProviderDescriptor>): AgentSessionProviderMenuModel {
  val standardItems = ArrayList<AgentSessionProviderMenuItem>(bridges.size)
  val yoloItems = ArrayList<AgentSessionProviderMenuItem>()

  bridges.forEach { bridge ->
    val cliAvailable = bridge.isCliAvailable()
    appendMenuItems(bridge, cliAvailable, standardItems, yoloItems)
  }

  return AgentSessionProviderMenuModel(
    standardItems = standardItems,
    yoloItems = yoloItems,
  )
}

/**
 * Suspending variant of [buildAgentSessionProviderMenuModel] that uses [AgentSessionProviderDescriptor.ensureCliAvailable]
 * for the enable/disable decision, so menus rendered through it stay aligned with the launch-time CLI lookup
 * (`TerminalAgentResolver`). Surfaces that can repaint asynchronously (the agent prompt palette) prefer this
 * over the synchronous [buildAgentSessionProviderMenuModel].
 */
suspend fun buildAgentSessionProviderMenuModelAsync(
  bridges: List<AgentSessionProviderDescriptor>,
): AgentSessionProviderMenuModel {
  val standardItems = ArrayList<AgentSessionProviderMenuItem>(bridges.size)
  val yoloItems = ArrayList<AgentSessionProviderMenuItem>()

  for (bridge in bridges) {
    val cliAvailable = bridge.ensureCliAvailable()
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

fun buildAgentSessionProviderActionModel(
    bridges: List<AgentSessionProviderDescriptor>,
    lastUsedProvider: AgentSessionProvider?,
    lastUsedLaunchMode: AgentSessionLaunchMode? = null,
): AgentSessionProviderActionModel {
  val menuModel = buildAgentSessionProviderMenuModel(bridges)
  return AgentSessionProviderActionModel(
    menuModel = menuModel,
    quickStartItem = resolveQuickStartProviderItem(menuModel, lastUsedProvider, lastUsedLaunchMode),
  )
}

private fun resolveQuickStartProviderItem(
  menuModel: AgentSessionProviderMenuModel,
  lastUsedProvider: AgentSessionProvider?,
  lastUsedLaunchMode: AgentSessionLaunchMode?,
): AgentSessionProviderMenuItem? {
  if (lastUsedLaunchMode == AgentSessionLaunchMode.YOLO) {
    val yoloItems = menuModel.yoloItems.filter { it.isEnabled }
    val preferredYoloItem = lastUsedProvider?.let { provider ->
      yoloItems.firstOrNull { item -> item.bridge.provider == provider }
    }
    if (preferredYoloItem != null) return preferredYoloItem
  }

  val standardItems = menuModel.standardItems.filter { it.isEnabled }
  if (standardItems.isEmpty()) return null

  val preferredItem = lastUsedProvider?.let { provider ->
    standardItems.firstOrNull { item -> item.bridge.provider == provider }
  }
  return preferredItem ?: standardItems.first()
}
