// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatTabSelection
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.AgentSubAgent
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.resolveSelectedSessionTreeId
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class SessionTreeSelectionSyncTest {
  @Test
  fun resolvesProjectThreadSelection() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        threads = listOf(
          AgentSessionThread(
            id = "thread-1",
            title = "Thread 1",
            updatedAt = 100,
            archived = false,
            provider = AgentSessionProvider.CODEX,
          )
        ),
      )
    )

    val selection = AgentChatTabSelection(
      projectPath = "/work/project-a",
      threadIdentity = "codex:thread-1",
      threadId = "thread-1",
      subAgentId = null,
    )

    val selectedId = resolveSelectedSessionTreeId(projects, selection)

    assertThat(selectedId).isEqualTo(SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1"))
  }

  @Test
  fun resolvesProjectSubAgentSelectionWithThreadFallback() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        threads = listOf(
          AgentSessionThread(
            id = "thread-1",
            title = "Thread 1",
            updatedAt = 100,
            archived = false,
            provider = AgentSessionProvider.CODEX,
            subAgents = listOf(AgentSubAgent(id = "alpha", name = "Alpha")),
          )
        ),
      )
    )

    val selectedSubAgent = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/project-a",
        threadIdentity = "codex:thread-1",
        threadId = "thread-1",
        subAgentId = "alpha",
      ),
    )
    val fallbackToThread = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/project-a",
        threadIdentity = "codex:thread-1",
        threadId = "thread-1",
        subAgentId = "beta",
      ),
    )

    assertThat(selectedSubAgent)
      .isEqualTo(SessionTreeId.SubAgent("/work/project-a", AgentSessionProvider.CODEX, "thread-1", "alpha"))
    assertThat(fallbackToThread).isEqualTo(SessionTreeId.Thread("/work/project-a", AgentSessionProvider.CODEX, "thread-1"))
  }

  @Test
  fun resolvesWorktreeThreadAndSubAgentSelection() {
    val worktreeThread = AgentSessionThread(
      id = "thread-wt",
      title = "Worktree Thread",
      updatedAt = 200,
      archived = false,
      provider = AgentSessionProvider.CLAUDE,
      subAgents = listOf(AgentSubAgent(id = "agent-1", name = "Agent 1")),
    )
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
        worktrees = listOf(
          AgentWorktree(
            path = "/work/project-feature",
            name = "project-feature",
            branch = "feature",
            isOpen = false,
            threads = listOf(worktreeThread),
          )
        ),
      )
    )

    val threadSelection = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/project-feature",
        threadIdentity = "claude:thread-wt",
        threadId = "thread-wt",
        subAgentId = null,
      ),
    )
    val subAgentSelection = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/project-feature",
        threadIdentity = "claude:thread-wt",
        threadId = "thread-wt",
        subAgentId = "agent-1",
      ),
    )

    assertThat(threadSelection)
      .isEqualTo(SessionTreeId.WorktreeThread("/work/project-a", "/work/project-feature", AgentSessionProvider.CLAUDE, "thread-wt"))
    assertThat(subAgentSelection)
      .isEqualTo(
        SessionTreeId.WorktreeSubAgent(
          "/work/project-a",
          "/work/project-feature",
          AgentSessionProvider.CLAUDE,
          "thread-wt",
          "agent-1",
        )
      )
  }

  @Test
  fun returnsNullForUnknownOrMalformedSelection() {
    val projects = listOf(
      AgentProjectSessions(
        path = "/work/project-a",
        name = "Project A",
        isOpen = true,
      )
    )

    val malformedIdentity = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/project-a",
        threadIdentity = "malformed",
        threadId = "thread-1",
        subAgentId = null,
      ),
    )
    val unknownPath = resolveSelectedSessionTreeId(
      projects,
      AgentChatTabSelection(
        projectPath = "/work/missing",
        threadIdentity = "codex:thread-1",
        threadId = "thread-1",
        subAgentId = null,
      ),
    )

    assertThat(malformedIdentity).isNull()
    assertThat(unknownPath).isNull()
  }
}
