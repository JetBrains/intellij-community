// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.tree.pathForMoreThreadsNode
import com.intellij.agent.workbench.sessions.tree.shouldHandleSingleClick
import com.intellij.agent.workbench.sessions.tree.shouldOpenOnActivation
import com.intellij.agent.workbench.sessions.tree.shouldRetargetSelectionForContextMenu
import com.intellij.agent.workbench.sessions.ui.resolveArchiveActionContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsSwingTreeInteractionTest {
  @Test
  fun singleClickActionIsReservedForMoreRows() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)

    assertThat(shouldHandleSingleClick(SessionTreeNode.MoreProjects(hiddenCount = 2))).isTrue()
    assertThat(shouldHandleSingleClick(SessionTreeNode.MoreThreads(project = project, hiddenCount = 4))).isTrue()
    assertThat(shouldHandleSingleClick(SessionTreeNode.Project(project))).isFalse()
    assertThat(shouldHandleSingleClick(SessionTreeNode.Warning("warning"))).isFalse()
  }

  @Test
  fun activationOpenPolicyIncludesProjectAndWorktreeRows() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = false)
    val worktree = AgentWorktree(
      path = "/work/project-a-feature",
      name = "project-a-feature",
      branch = "feature",
      isOpen = false,
    )
    val thread = AgentSessionThread(id = "thread-1", title = "Thread 1", updatedAt = 100, archived = false)
    val pendingThread = AgentSessionThread(id = "new-1", title = "New Thread", updatedAt = 100, archived = false)
    val subAgent = AgentSubAgent(id = "sub-1", name = "Sub Agent")

    assertThat(shouldOpenOnActivation(SessionTreeNode.Project(project))).isTrue()
    assertThat(shouldOpenOnActivation(SessionTreeNode.Worktree(project, worktree))).isTrue()
    assertThat(shouldOpenOnActivation(SessionTreeNode.Thread(project, thread))).isTrue()
    assertThat(shouldOpenOnActivation(SessionTreeNode.Thread(project, pendingThread))).isFalse()
    assertThat(shouldOpenOnActivation(SessionTreeNode.SubAgent(project, thread, subAgent))).isTrue()
    assertThat(shouldOpenOnActivation(SessionTreeNode.MoreProjects(hiddenCount = 1))).isFalse()
    assertThat(shouldOpenOnActivation(SessionTreeNode.MoreThreads(project, hiddenCount = 1))).isFalse()
    assertThat(shouldOpenOnActivation(SessionTreeNode.Warning("warning"))).isFalse()
  }

  @Test
  fun resolvesMoreThreadPathForProjectAndWorktreeRows() {
    assertThat(pathForMoreThreadsNode(SessionTreeId.MoreThreads(projectPath = "/work/project-a")))
      .isEqualTo("/work/project-a")
    assertThat(
      pathForMoreThreadsNode(
        SessionTreeId.WorktreeMoreThreads(
          projectPath = "/work/project-a",
          worktreePath = "/work/project-feature",
        )
      )
    ).isEqualTo("/work/project-feature")
    assertThat(pathForMoreThreadsNode(SessionTreeId.MoreProjects)).isNull()
  }

  @Test
  fun contextMenuSelectionRetargetPolicyMatchesIjTreeConventions() {
    assertThat(shouldRetargetSelectionForContextMenu(isClickedPathSelected = false)).isTrue()
    assertThat(shouldRetargetSelectionForContextMenu(isClickedPathSelected = true)).isFalse()
  }

  @Test
  fun archiveActionContextPrefersPopupContextAndFallsBackToSelection() {
    val project = ProjectManager.getInstance().defaultProject
    val projectSessions = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val popupThread = AgentSessionThread(id = "popup-1", title = "Popup", updatedAt = 100, archived = false, provider = AgentSessionProvider.CODEX)
    val selectedThread = AgentSessionThread(id = "selected-1", title = "Selected", updatedAt = 100, archived = false, provider = AgentSessionProvider.CLAUDE)
    val popupTarget = ArchiveThreadTarget(path = "/work/project-a", provider = popupThread.provider, threadId = popupThread.id)
    val selectedTarget = ArchiveThreadTarget(path = "/work/project-b", provider = selectedThread.provider, threadId = selectedThread.id)
    val popupContext = AgentSessionsTreePopupActionContext(
      project = project,
      nodeId = SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "popup-1"),
      node = SessionTreeNode.Thread(projectSessions, popupThread),
      archiveTargets = listOf(popupTarget),
    )

    val contextFromPopup = resolveArchiveActionContext(
      popupActionContext = popupContext,
      project = project,
      selectedTreeId = SessionTreeId.Thread("/work/project-b", AgentSessionProvider.CLAUDE, "selected-1"),
      selectedTreeNode = SessionTreeNode.Thread(projectSessions, selectedThread),
      selectedArchiveTargets = listOf(selectedTarget),
    )
    assertThat(contextFromPopup).isEqualTo(popupContext)

    val contextFromSelection = resolveArchiveActionContext(
      popupActionContext = null,
      project = project,
      selectedTreeId = SessionTreeId.Thread("/work/project-b", AgentSessionProvider.CLAUDE, "selected-1"),
      selectedTreeNode = SessionTreeNode.Thread(projectSessions, selectedThread),
      selectedArchiveTargets = listOf(selectedTarget),
    )
    assertThat(contextFromSelection).isEqualTo(
      AgentSessionsTreePopupActionContext(
        project = project,
        nodeId = SessionTreeId.Thread("/work/project-b", AgentSessionProvider.CLAUDE, "selected-1"),
        node = SessionTreeNode.Thread(projectSessions, selectedThread),
        archiveTargets = listOf(selectedTarget),
      )
    )

    val missingSelectionContext = resolveArchiveActionContext(
      popupActionContext = null,
      project = project,
      selectedTreeId = null,
      selectedTreeNode = null,
      selectedArchiveTargets = listOf(selectedTarget),
    )
    assertThat(missingSelectionContext).isNull()
  }
}
