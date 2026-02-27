// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

internal fun createNewThreadViaService(
  path: String,
  provider: AgentSessionProvider,
  mode: AgentSessionLaunchMode,
  currentProject: Project,
) {
  service<AgentSessionsService>().createNewSession(path, provider, mode, currentProject)
}

internal fun buildNewThreadMenuModel(bridges: List<AgentSessionProviderBridge>): NewThreadMenuModel {
  val standardItems = ArrayList<NewThreadMenuItem>(bridges.size)
  val yoloItems = ArrayList<NewThreadMenuItem>()

  bridges.forEach { bridge ->
    if (AgentSessionLaunchMode.STANDARD in bridge.supportedLaunchModes) {
      standardItems += NewThreadMenuItem(
        bridge = bridge,
        mode = AgentSessionLaunchMode.STANDARD,
        label = AgentSessionsBundle.message(bridge.newSessionLabelKey),
        isEnabled = true,
      )
    }

    val yoloLabelKey = bridge.yoloSessionLabelKey
    if (yoloLabelKey != null && AgentSessionLaunchMode.YOLO in bridge.supportedLaunchModes) {
      yoloItems += NewThreadMenuItem(
        bridge = bridge,
        mode = AgentSessionLaunchMode.YOLO,
        label = AgentSessionsBundle.message(yoloLabelKey),
        isEnabled = true,
      )
    }
  }

  return NewThreadMenuModel(
    standardItems = standardItems,
    yoloItems = yoloItems,
  )
}

internal fun buildNewThreadActionModel(
  bridges: List<AgentSessionProviderBridge>,
  lastUsedProvider: AgentSessionProvider?,
): NewThreadActionModel {
  val menuModel = buildNewThreadMenuModel(bridges)
  return NewThreadActionModel(
    menuModel = menuModel,
    quickStartItem = resolveQuickStartThreadItem(menuModel, lastUsedProvider),
  )
}

internal fun launchQuickStartThread(
  path: String,
  project: Project,
  quickStartItem: NewThreadMenuItem?,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project) -> Unit,
) {
  val item = quickStartItem ?: return
  createNewSession(path, item.bridge.provider, AgentSessionLaunchMode.STANDARD, project)
}

internal fun NewThreadMenuModel.hasEntries(): Boolean {
  return standardItems.isNotEmpty() || yoloItems.isNotEmpty()
}

internal fun buildNewThreadMenuActions(
  path: String,
  project: Project,
  menuModel: NewThreadMenuModel,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project) -> Unit,
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
        createNewSession = createNewSession,
      )
    }
  }
  return actions.toTypedArray()
}

internal data class AgentSessionsEditorTabThreadCoordinates(
  val path: String,
  val provider: AgentSessionProvider,
  val threadId: String,
)

internal fun resolveAgentSessionsEditorTabThreadCoordinates(
  context: AgentChatEditorTabActionContext,
): AgentSessionsEditorTabThreadCoordinates? {
  if (context.isPendingThread) {
    return null
  }

  val provider = context.provider ?: return null
  val threadId = context.sessionId.takeIf { it.isNotBlank() } ?: return null
  return AgentSessionsEditorTabThreadCoordinates(
    path = context.path,
    provider = provider,
    threadId = threadId,
  )
}

internal fun resolveArchiveThreadTargetFromEditorTabContext(context: AgentChatEditorTabActionContext): ArchiveThreadTarget? {
  val threadCoordinates = resolveAgentSessionsEditorTabThreadCoordinates(context) ?: return null
  return ArchiveThreadTarget(
    path = threadCoordinates.path,
    provider = threadCoordinates.provider,
    threadId = threadCoordinates.threadId,
  )
}

private fun resolveQuickStartThreadItem(
  menuModel: NewThreadMenuModel,
  lastUsedProvider: AgentSessionProvider?,
): NewThreadMenuItem? {
  val standardItems = menuModel.standardItems.filter { it.isEnabled }
  if (standardItems.isEmpty()) return null

  val preferredItem = lastUsedProvider?.let { provider ->
    standardItems.firstOrNull { item -> item.bridge.provider == provider }
  }
  return preferredItem ?: standardItems.first()
}

private class AgentSessionsCreateThreadAction(
  private val path: String,
  private val item: NewThreadMenuItem,
  private val project: Project,
  private val createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project) -> Unit,
) : DumbAwareAction(item.label, null, providerIcon(item.bridge.provider)) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = item.isEnabled
    e.presentation.description = if (item.isEnabled) {
      null
    }
    else {
      AgentSessionsBundle.message(
        "toolwindow.action.new.session.unavailable",
        providerDisplayName(item.bridge.provider),
      )
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!item.isEnabled) return
    createNewSession(path, item.bridge.provider, item.mode, project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

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
