// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderActionModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderActionModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.ui.providerDisplayName
import com.intellij.agent.workbench.sessions.ui.providerIcon
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon

fun createNewThreadViaService(
  path: String,
  provider: AgentSessionProvider,
  mode: AgentSessionLaunchMode,
  currentProject: Project,
  entryPoint: AgentWorkbenchEntryPoint,
) {
  service<AgentSessionLaunchService>().createNewSession(path, provider, mode, currentProject)
}

fun buildNewThreadMenuModel(bridges: List<AgentSessionProviderBridge>): AgentSessionProviderMenuModel {
  return buildAgentSessionProviderMenuModel(bridges)
}

fun buildNewThreadActionModel(
  bridges: List<AgentSessionProviderBridge>,
  lastUsedProvider: AgentSessionProvider?,
  lastUsedLaunchMode: AgentSessionLaunchMode? = null,
): AgentSessionProviderActionModel {
  return buildAgentSessionProviderActionModel(bridges, lastUsedProvider, lastUsedLaunchMode)
}

fun launchQuickStartThread(
  path: String,
  project: Project,
  quickStartItem: AgentSessionProviderMenuItem?,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
) {
  val item = quickStartItem ?: return
  createNewSession(path, item.bridge.provider, item.mode, project, entryPoint)
}

fun buildNewThreadMenuActions(
  path: String,
  project: Project,
  menuModel: AgentSessionProviderMenuModel,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
): Array<AnAction> {
  if (!menuModel.hasEntries()) {
    return emptyArray()
  }

  val actions = ArrayList<AnAction>(menuModel.standardItems.size + menuModel.yoloItems.size + 2)
  menuModel.standardItems.forEach { item ->
    actions += AgentSessionsCreateThreadAction(
        path = path,
        item = item,
        project = project,
        entryPoint = entryPoint,
        createNewSession = createNewSession,
      )
  }
  if (menuModel.yoloItems.isNotEmpty()) {
    if (menuModel.standardItems.isNotEmpty()) {
      actions += Separator.getInstance()
    }
    actions += Separator.create(AgentSessionsBundle.message("toolwindow.action.new.session.section.auto"))
    menuModel.yoloItems.forEach { item ->
      actions += AgentSessionsCreateThreadAction(
        path = path,
        item = item,
        project = project,
        entryPoint = entryPoint,
        createNewSession = createNewSession,
      )
    }
  }
  return actions.toTypedArray()
}

private class AgentSessionsCreateThreadAction(
  private val path: String,
  private val item: AgentSessionProviderMenuItem,
  private val project: Project,
  private val entryPoint: AgentWorkbenchEntryPoint,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
) : DumbAwareAction(AgentSessionsBundle.message(item.labelKey), null, providerIconWithMode(item.bridge.provider, item.mode)) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = item.isEnabled
    e.presentation.description = if (item.isEnabled) {
      null
    }
    else {
      val key = item.disabledReasonKey
      if (key == null) {
        AgentSessionsBundle.message(
          "toolwindow.action.new.session.unavailable",
          providerDisplayName(item.bridge.provider),
        )
      }
      else {
        AgentSessionsBundle.message(key)
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!item.isEnabled) return
    createNewSession(path, item.bridge.provider, item.mode, project, entryPoint)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal fun providerDisplayName(provider: AgentSessionProvider): @NlsSafe String {
  val bridge = AgentSessionProviderBridges.find(provider) ?: return provider.value
  return runCatching { AgentSessionsBundle.message(bridge.displayNameKey) }
    .getOrDefault(bridge.displayNameFallback)
}

internal fun providerIcon(provider: AgentSessionProvider): Icon? {
  return AgentSessionProviderBridges.find(provider)?.icon
}

internal fun providerIconWithMode(provider: AgentSessionProvider, mode: AgentSessionLaunchMode): Icon? {
  val icon = providerIcon(provider) ?: return null
  if (mode == AgentSessionLaunchMode.YOLO) {
    return withYoloModeBadge(icon)
  }
  return icon
}
