// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.resolveAgentChatEditorTabActionContext
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.service.buildAgentSessionProjectPathCandidates
import com.intellij.agent.workbench.sessions.service.collectOpenAgentSessionProjectPaths
import com.intellij.agent.workbench.sessions.service.normalizeOpenableSourceProjectPath
import com.intellij.agent.workbench.sessions.service.selectedChatSourceProjectPath
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import javax.swing.JComponent

internal class AgentSessionsEditorTabNewThreadContext(
  val project: Project,
  private val resolveTarget: () -> AgentSessionsEditorTabNewThreadTarget?,
  private val resolveTargetForUpdate: () -> AgentSessionsEditorTabNewThreadTarget? = resolveTarget,
) {
  constructor(
    project: Project,
    resolveTarget: () -> AgentSessionsEditorTabNewThreadTarget?,
  ) : this(project = project, resolveTarget = resolveTarget, resolveTargetForUpdate = resolveTarget)

  val target: AgentSessionsEditorTabNewThreadTarget?
    get() = resolveTarget()

  val targetForUpdate: AgentSessionsEditorTabNewThreadTarget?
    get() = resolveTargetForUpdate()
}

internal sealed class AgentSessionsEditorTabNewThreadTarget {
  data class Direct(val path: String) : AgentSessionsEditorTabNewThreadTarget()
  data class Candidates(val candidates: List<AgentPromptProjectPathCandidate>) : AgentSessionsEditorTabNewThreadTarget()
}

internal fun resolveAgentSessionsEditorTabNewThreadContext(
  event: AnActionEvent,
  isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
  openInDedicatedFrame: () -> Boolean = ::openChatInDedicatedFrame,
  openProjectPaths: () -> List<String> = ::collectOpenAgentSessionProjectPaths,
  resolveChatContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
): AgentSessionsEditorTabNewThreadContext? {
  val project = event.project ?: return null
  val chatContext = resolveChatContext(event)
  return when {
    isDedicatedProject(project) -> AgentSessionsEditorTabNewThreadContext(project) {
      resolveDedicatedFrameNewThreadTarget(chatContext, openProjectPaths)
    }
    openInDedicatedFrame() -> null
    else -> {
      val target = resolveProjectFrameNewThreadTarget(project, chatContext) ?: return null
      AgentSessionsEditorTabNewThreadContext(project) { target }
    }
  }
}

internal fun resolveAgentSessionsMainToolbarNewThreadContext(
  event: AnActionEvent,
  isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
  openProjectPaths: () -> List<String> = ::collectOpenAgentSessionProjectPaths,
  resolveChatContext: (AnActionEvent) -> AgentChatEditorTabActionContext? = ::resolveAgentChatEditorTabActionContext,
  selectedSourcePath: (Project) -> String? = ::selectedChatSourceProjectPath,
): AgentSessionsEditorTabNewThreadContext? {
  val project = event.project ?: return null
  val chatContext = resolveChatContext(event)
  return if (isDedicatedProject(project)) {
    AgentSessionsEditorTabNewThreadContext(
      project = project,
      resolveTarget = { resolveDedicatedFrameNewThreadTarget(chatContext, openProjectPaths) },
      resolveTargetForUpdate = { null },
    )
  }
  else {
    val target = resolveMainToolbarProjectFrameNewThreadTarget(project, chatContext, selectedSourcePath) ?: return null
    AgentSessionsEditorTabNewThreadContext(project) { target }
  }
}

private fun resolveDedicatedFrameNewThreadTarget(
  chatContext: AgentChatEditorTabActionContext?,
  openProjectPaths: () -> List<String>,
): AgentSessionsEditorTabNewThreadTarget? {
  // Source-project candidates are resolved lazily on click/popup open. In a multi-project dedicated frame,
  // require an explicit choice instead of silently using the active Agent tab's source path.
  val candidates = buildAgentSessionProjectPathCandidates(openProjectPaths())
  return when (candidates.size) {
    0 -> chatContext?.let { AgentSessionsEditorTabNewThreadTarget.Direct(it.path) }
    1 -> AgentSessionsEditorTabNewThreadTarget.Direct(candidates.single().path)
    else -> AgentSessionsEditorTabNewThreadTarget.Candidates(candidates)
  }
}

private fun resolveProjectFrameNewThreadTarget(
  project: Project,
  chatContext: AgentChatEditorTabActionContext?,
): AgentSessionsEditorTabNewThreadTarget? {
  val path = chatContext?.path ?: normalizeOpenableSourceProjectPath(project.basePath) ?: return null
  return AgentSessionsEditorTabNewThreadTarget.Direct(path)
}

private fun resolveMainToolbarProjectFrameNewThreadTarget(
  project: Project,
  chatContext: AgentChatEditorTabActionContext?,
  selectedSourcePath: (Project) -> String?,
): AgentSessionsEditorTabNewThreadTarget? {
  val path = chatContext?.path
             ?: normalizeOpenableSourceProjectPath(selectedSourcePath(project))
             ?: normalizeOpenableSourceProjectPath(project.basePath)
             ?: return null
  return AgentSessionsEditorTabNewThreadTarget.Direct(path)
}

internal fun buildProjectCandidatePopupGroup(
  candidate: AgentPromptProjectPathCandidate,
  project: Project,
  menuModel: AgentSessionProviderMenuModel,
  entryPoint: AgentWorkbenchEntryPoint,
  createNewSession: (String, AgentSessionProvider, AgentSessionLaunchMode, Project, AgentWorkbenchEntryPoint) -> Unit,
): ActionGroup {
  val group = DumbAwareDefaultActionGroup(candidate.displayName, true)
  buildNewThreadMenuActions(
    path = candidate.path,
    project = project,
    menuModel = menuModel,
    entryPoint = entryPoint,
    createNewSession = createNewSession,
  ).forEach(group::add)
  return group
}

internal fun resolveQuickStartProjectPopupAnchor(e: AnActionEvent): JComponent? {
  return (e.inputEvent?.component as? JComponent)
         ?: (e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? JComponent)
}

private fun openChatInDedicatedFrame(): Boolean {
  return AgentChatOpenModeSettings.openInDedicatedFrame()
}
