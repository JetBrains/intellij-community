// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.actions

import com.intellij.agent.workbench.thread.view.AgentThreadViewEditorTabActionContext
import com.intellij.agent.workbench.thread.view.resolveAgentThreadViewEditorTabActionContext
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathCandidate
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.agent.workbench.sessions.service.buildAgentSessionProjectPathCandidates
import com.intellij.agent.workbench.sessions.service.collectOpenAgentSessionProjectPaths
import com.intellij.agent.workbench.sessions.service.normalizeOpenableSourceProjectPath
import com.intellij.agent.workbench.sessions.service.selectedThreadViewSourceProjectPath
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import javax.swing.JComponent

internal class AgentSessionsNewThreadContext(
  val project: Project,
  private val resolveTarget: () -> AgentSessionsNewThreadTarget?,
  private val resolveTargetForUpdate: () -> AgentSessionsNewThreadTarget? = resolveTarget,
) {
  constructor(
    project: Project,
    resolveTarget: () -> AgentSessionsNewThreadTarget?,
  ) : this(project = project, resolveTarget = resolveTarget, resolveTargetForUpdate = resolveTarget)

  val target: AgentSessionsNewThreadTarget?
    get() = resolveTarget()

  val targetForUpdate: AgentSessionsNewThreadTarget?
    get() = resolveTargetForUpdate()
}

internal sealed class AgentSessionsNewThreadTarget {
  data class Direct(val path: String) : AgentSessionsNewThreadTarget()
  data class Candidates(val candidates: List<AgentPromptProjectPathCandidate>) : AgentSessionsNewThreadTarget()
}

internal fun resolveAgentSessionsMainToolbarNewThreadContext(
  event: AnActionEvent,
  isDedicatedProject: (Project) -> Boolean = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
  openProjectPaths: () -> List<String> = ::collectOpenAgentSessionProjectPaths,
  resolveThreadViewContext: (AnActionEvent) -> AgentThreadViewEditorTabActionContext? = ::resolveAgentThreadViewEditorTabActionContext,
  selectedSourcePath: (Project) -> String? = ::selectedThreadViewSourceProjectPath,
): AgentSessionsNewThreadContext? {
  val project = event.project ?: return null
  val threadViewContext = resolveThreadViewContext(event)
  return if (isDedicatedProject(project)) {
    AgentSessionsNewThreadContext(
      project = project,
      resolveTarget = { resolveDedicatedFrameNewThreadTarget(threadViewContext, openProjectPaths) },
      resolveTargetForUpdate = { null },
    )
  }
  else {
    val target = resolveMainToolbarProjectFrameNewThreadTarget(project, threadViewContext, selectedSourcePath) ?: return null
    AgentSessionsNewThreadContext(project) { target }
  }
}

private fun resolveDedicatedFrameNewThreadTarget(
  threadViewContext: AgentThreadViewEditorTabActionContext?,
  openProjectPaths: () -> List<String>,
): AgentSessionsNewThreadTarget? {
  // Source-project candidates are resolved lazily on click/popup open. In a multi-project dedicated frame,
  // require an explicit choice instead of silently using the active Agent tab's source path.
  val candidates = buildAgentSessionProjectPathCandidates(openProjectPaths())
  return when (candidates.size) {
    0 -> threadViewContext?.let { AgentSessionsNewThreadTarget.Direct(it.path) }
    1 -> AgentSessionsNewThreadTarget.Direct(candidates.single().path)
    else -> AgentSessionsNewThreadTarget.Candidates(candidates)
  }
}

private fun resolveMainToolbarProjectFrameNewThreadTarget(
  project: Project,
  threadViewContext: AgentThreadViewEditorTabActionContext?,
  selectedSourcePath: (Project) -> String?,
): AgentSessionsNewThreadTarget? {
  val path = threadViewContext?.path
             ?: normalizeOpenableSourceProjectPath(selectedSourcePath(project))
             ?: normalizeOpenableSourceProjectPath(project.basePath)
             ?: return null
  return AgentSessionsNewThreadTarget.Direct(path)
}

internal fun resolveQuickStartProjectPopupAnchor(e: AnActionEvent): JComponent? {
  return (e.inputEvent?.component as? JComponent)
         ?: (e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? JComponent)
}
