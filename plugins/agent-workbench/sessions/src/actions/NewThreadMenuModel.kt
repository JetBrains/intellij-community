// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import org.jetbrains.annotations.Nls

internal data class NewThreadMenuModel(
  val standardItems: List<NewThreadMenuItem>,
  val yoloItems: List<NewThreadMenuItem>,
)

internal data class NewThreadActionModel(
  val menuModel: NewThreadMenuModel,
  val quickStartItem: NewThreadMenuItem?,
)

internal data class NewThreadMenuItem(
  val bridge: AgentSessionProviderBridge,
  val mode: AgentSessionLaunchMode,
  val label: @Nls String,
  val isEnabled: Boolean,
)

internal fun NewThreadMenuModel.hasEntries(): Boolean {
  return standardItems.isNotEmpty() || yoloItems.isNotEmpty()
}
