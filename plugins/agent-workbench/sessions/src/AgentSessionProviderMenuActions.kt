// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.withYoloModeBadge
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.LafIconLookup
import org.jetbrains.annotations.Nls
import javax.swing.Icon

fun buildAgentSessionProviderMenuActions(
  menuModel: AgentSessionProviderMenuModel,
  onItemSelected: (AgentSessionProviderMenuItem) -> Unit,
): Array<AnAction> {
  val actions = ArrayList<AnAction>(menuModel.standardItems.size + menuModel.yoloItems.size + 2)
  menuModel.standardItems.forEach { item ->
    actions += AgentSessionProviderMenuItemAction(item, onItemSelected)
  }
  if (menuModel.yoloItems.isNotEmpty()) {
    if (menuModel.standardItems.isNotEmpty()) {
      actions += Separator.getInstance()
    }
    actions += Separator.create(AgentSessionsBundle.message("toolwindow.action.new.session.section.auto"))
    menuModel.yoloItems.forEach { item ->
      actions += AgentSessionProviderMenuItemAction(item, onItemSelected)
    }
  }
  return actions.toTypedArray()
}

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

fun setProviderItemLaunchProfileIcon(presentation: Presentation, item: AgentSessionProviderMenuItem, selected: Boolean) {
  setLaunchProfileIcon(presentation, providerItemMonochromeIconWithMode(item), selected)
}

fun setLaunchProfileIcon(presentation: Presentation, baseIcon: Icon, selected: Boolean) {
  Toggleable.setSelected(presentation, selected)
  presentation.icon = baseIcon
  presentation.selectedIcon = null
  presentation.disabledIcon = null
  presentation.putClientProperty(
    ActionUtil.SECONDARY_ICON,
    when {
      !selected -> null
      presentation.isEnabled -> LafIconLookup.getIcon("checkmark")
      else -> LafIconLookup.getDisabledIcon("checkmark")
    },
  )
}

private class AgentSessionProviderMenuItemAction(
  private val item: AgentSessionProviderMenuItem,
  private val onItemSelected: (AgentSessionProviderMenuItem) -> Unit,
) : DumbAwareAction(AgentSessionsBundle.message(item.labelKey), null, providerItemIconWithMode(item)) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = item.isEnabled
    e.presentation.description = if (item.isEnabled) enabledProviderDescription(item) else disabledProviderReason(item)
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!item.isEnabled) return
    onItemSelected(item)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private fun enabledProviderDescription(item: AgentSessionProviderMenuItem): @Nls String? {
  val descriptionKey = item.bridge.newSessionDescriptionKey ?: return null
  return AgentSessionsBundle.message(descriptionKey)
}

private fun disabledProviderReason(item: AgentSessionProviderMenuItem): @Nls String {
  val reasonKey = item.disabledReasonKey
  if (reasonKey != null) {
    return AgentSessionsBundle.message(reasonKey)
  }
  return AgentSessionsBundle.message(
    "toolwindow.action.new.session.unavailable",
    providerDisplayName(item.bridge.provider),
  )
}
