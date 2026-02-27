// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.tree.pathForMoreThreadsNode
import com.intellij.agent.workbench.sessions.tree.shouldHandleSingleClick
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionTreeInteractionTest {
  @Test
  fun singleClickActionIsReservedForMoreRows() {
    val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)

    assertTrue(shouldHandleSingleClick(SessionTreeNode.MoreProjects(hiddenCount = 2)))
    assertTrue(shouldHandleSingleClick(SessionTreeNode.MoreThreads(project = project, hiddenCount = 4)))
    assertFalse(shouldHandleSingleClick(SessionTreeNode.Project(project)))
    assertFalse(shouldHandleSingleClick(SessionTreeNode.Warning("warning")))
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
}
