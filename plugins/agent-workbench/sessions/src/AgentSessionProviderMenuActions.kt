// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.withYoloModeBadge
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAwareAction
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

fun providerIconWithMode(provider: AgentSessionProvider, mode: AgentSessionLaunchMode): Icon? {
  val icon = providerIcon(provider) ?: return null
  if (mode == AgentSessionLaunchMode.YOLO) {
    return withYoloModeBadge(icon)
  }
  return icon
}

private class AgentSessionProviderMenuItemAction(
  private val item: AgentSessionProviderMenuItem,
  private val onItemSelected: (AgentSessionProviderMenuItem) -> Unit,
) : DumbAwareAction(AgentSessionsBundle.message(item.labelKey), null, providerIconWithMode(item.bridge.provider, item.mode)) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = item.isEnabled
    e.presentation.description = if (item.isEnabled) null else disabledProviderReason(item)
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!item.isEnabled) return
    onItemSelected(item)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
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
