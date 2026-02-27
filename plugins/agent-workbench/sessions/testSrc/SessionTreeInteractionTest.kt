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

    assertThat(shouldHandleSingleClick(SessionTreeNode.MoreProjects(hiddenCount = 2))).isTrue()
    assertThat(shouldHandleSingleClick(SessionTreeNode.MoreThreads(project = project, hiddenCount = 4))).isTrue()
    assertThat(shouldHandleSingleClick(SessionTreeNode.Project(project))).isFalse()
    assertThat(shouldHandleSingleClick(SessionTreeNode.Warning("warning"))).isFalse()
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
}
