// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsSwingTreeInteractionTest {
  @Test
  fun singleClickActionIsReservedForMoreRows() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)

    assertTrue(shouldHandleSingleClick(SessionTreeNode.MoreProjects(hiddenCount = 2)))
    assertTrue(shouldHandleSingleClick(SessionTreeNode.MoreThreads(project = project, hiddenCount = 4)))
    assertFalse(shouldHandleSingleClick(SessionTreeNode.Project(project)))
    assertFalse(shouldHandleSingleClick(SessionTreeNode.Warning("warning")))
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
    val subAgent = AgentSubAgent(id = "sub-1", name = "Sub Agent")

    assertTrue(shouldOpenOnActivation(SessionTreeNode.Project(project)))
    assertTrue(shouldOpenOnActivation(SessionTreeNode.Worktree(project, worktree)))
    assertTrue(shouldOpenOnActivation(SessionTreeNode.Thread(project, thread)))
    assertTrue(shouldOpenOnActivation(SessionTreeNode.SubAgent(project, thread, subAgent)))
    assertFalse(shouldOpenOnActivation(SessionTreeNode.MoreProjects(hiddenCount = 1)))
    assertFalse(shouldOpenOnActivation(SessionTreeNode.MoreThreads(project, hiddenCount = 1)))
    assertFalse(shouldOpenOnActivation(SessionTreeNode.Warning("warning")))
  }

  @Test
  fun resolvesMoreThreadPathForProjectAndWorktreeRows() {
    assertEquals(
      "/work/project-a",
      pathForMoreThreadsNode(SessionTreeId.MoreThreads(projectPath = "/work/project-a")),
    )
    assertEquals(
      "/work/project-feature",
      pathForMoreThreadsNode(
        SessionTreeId.WorktreeMoreThreads(
          projectPath = "/work/project-a",
          worktreePath = "/work/project-feature",
        )
      ),
    )
    assertEquals(null, pathForMoreThreadsNode(SessionTreeId.MoreProjects))
  }

  @Test
  fun contextMenuSelectionRetargetPolicyMatchesIjTreeConventions() {
    assertTrue(shouldRetargetSelectionForContextMenu(isClickedPathSelected = false))
    assertFalse(shouldRetargetSelectionForContextMenu(isClickedPathSelected = true))
  }

  @Test
  fun archiveActionContextPrefersPopupContextAndFallsBackToSelection() {
    val project = ProjectManager.getInstance().defaultProject
    val projectSessions = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
    val popupThread = AgentSessionThread(id = "popup-1", title = "Popup", updatedAt = 100, archived = false, provider = AgentSessionProvider.CODEX)
    val selectedThread = AgentSessionThread(id = "selected-1", title = "Selected", updatedAt = 100, archived = false, provider = AgentSessionProvider.CLAUDE)
    val popupTarget = ArchiveThreadTarget(path = "/work/project-a", thread = popupThread)
    val selectedTarget = ArchiveThreadTarget(path = "/work/project-b", thread = selectedThread)
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
    assertEquals(popupContext, contextFromPopup)

    val contextFromSelection = resolveArchiveActionContext(
      popupActionContext = null,
      project = project,
      selectedTreeId = SessionTreeId.Thread("/work/project-b", AgentSessionProvider.CLAUDE, "selected-1"),
      selectedTreeNode = SessionTreeNode.Thread(projectSessions, selectedThread),
      selectedArchiveTargets = listOf(selectedTarget),
    )
    assertEquals(
      AgentSessionsTreePopupActionContext(
        project = project,
        nodeId = SessionTreeId.Thread("/work/project-b", AgentSessionProvider.CLAUDE, "selected-1"),
        node = SessionTreeNode.Thread(projectSessions, selectedThread),
        archiveTargets = listOf(selectedTarget),
      ),
      contextFromSelection,
    )

    val missingSelectionContext = resolveArchiveActionContext(
      popupActionContext = null,
      project = project,
      selectedTreeId = null,
      selectedTreeNode = null,
      selectedArchiveTargets = listOf(selectedTarget),
    )
    assertNull(missingSelectionContext)
  }
}
