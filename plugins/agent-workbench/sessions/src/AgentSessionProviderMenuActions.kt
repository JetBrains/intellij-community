// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.withYoloModeBadge
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.LafIconLookup
import org.jetbrains.annotations.Nls
import javax.swing.Icon

fun providerItemIconWithMode(item: AgentSessionProviderMenuItem): Icon {
  val icon = item.bridge.icon
  if (item.mode == AgentSessionLaunchMode.YOLO) {
    return withYoloModeBadge(icon)
  }
  return icon
}

fun providerItemMonochromeIconWithMode(item: AgentSessionProviderMenuItem): Icon {
  val useMonochrome = Registry.`is`("agent.workbench.use.monochrome.icons", true)
  val icon = if (useMonochrome) item.bridge.monochromeIcon else item.bridge.icon
  if (item.mode == AgentSessionLaunchMode.YOLO) {
    return withYoloModeBadge(icon)
  }
  return icon
}

fun setProviderItemLaunchProfileActiveMarker(presentation: Presentation, item: AgentSessionProviderMenuItem, active: Boolean) {
  setLaunchProfileActiveMarker(presentation, providerItemMonochromeIconWithMode(item), active)
}

fun setLaunchProfileIcon(presentation: Presentation, baseIcon: Icon, selected: Boolean) {
  Toggleable.setSelected(presentation, selected)
  setLaunchProfileActiveMarker(presentation, baseIcon, selected)
}

/**
 * Marks the active launch profile with a trailing checkmark without turning the action into a selected
 * [Toggleable]. A selected toggleable action that also carries an icon is rendered by the platform action
 * menu with a `PoppedIcon` background behind the icon (the "selected toolbar button" look), which is
 * redundant with the checkmark.
 */
fun setLaunchProfileActiveMarker(presentation: Presentation, baseIcon: Icon, active: Boolean) {
  presentation.icon = baseIcon
  presentation.selectedIcon = null
  presentation.disabledIcon = null
  presentation.putClientProperty(
    ActionUtil.SECONDARY_ICON,
    when {
      !active -> null
      presentation.isEnabled -> LafIconLookup.getIcon("checkmark")
      else -> LafIconLookup.getDisabledIcon("checkmark")
    },
  )
}

fun providerMenuItemDisabledReason(item: AgentSessionProviderMenuItem): @Nls String {
  val reasonKey = item.disabledReasonKey
  if (reasonKey != null) {
    return AgentSessionsBundle.message(reasonKey)
  }
  return AgentSessionsBundle.message(
    "toolwindow.action.new.session.unavailable",
    providerDisplayName(item.bridge.provider),
  )
}
