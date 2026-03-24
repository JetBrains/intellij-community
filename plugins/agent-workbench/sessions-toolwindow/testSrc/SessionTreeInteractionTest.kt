// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.copyPathForSessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.pathForMoreThreadsNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.shouldHandleSingleClick
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

  @Test
  fun resolvesCopyPathForProjectAndWorktreeRowsOnly() {
    assertThat(copyPathForSessionTreeId(SessionTreeId.Project(path = "/work/project-a")))
      .isEqualTo("/work/project-a")
    assertThat(
      copyPathForSessionTreeId(
        SessionTreeId.Worktree(projectPath = "/work/project-a", worktreePath = "/work/project-feature")
      )
    ).isEqualTo("/work/project-feature")
    assertThat(
      copyPathForSessionTreeId(
        SessionTreeId.Thread(projectPath = "/work/project-a", provider = AgentSessionProvider.CODEX, threadId = "thread-1")
      )
    ).isNull()
    assertThat(copyPathForSessionTreeId(SessionTreeId.MoreProjects)).isNull()
  }
}
