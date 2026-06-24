// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatEditorTabActionContext
import com.intellij.agent.workbench.chat.AgentChatThreadCoordinates
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentSessionsState
import com.intellij.agent.workbench.sessions.service.AgentSessionReadService
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class PendingThreadRebindTargetResolverTest {
  @Test
  fun resolveReturnsLightweightRebindTarget() {
    val readService = AgentSessionReadService(
      stateProvider = {
        AgentSessionsState(
          projects = listOf(
            AgentProjectSessions(
              path = PROJECT_PATH,
              name = "Project A",
              isOpen = true,
              providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
              threads = listOf(thread(id = "codex-42", updatedAt = 500L, provider = AgentSessionProvider.from("codex"), title = "Recovered")),
            )
          )
        )
      },
    )

    val context = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = PROJECT_PATH,
      tabKey = "pending-codex:new-1",
      threadIdentity = "codex:new-1",
      threadCoordinates = AgentChatThreadCoordinates(
        provider = AgentSessionProvider.from("codex"),
        sessionId = "new-1",
        isPending = true,
      ),
    )

    val target = readService.resolvePendingThreadRebindTarget(context, AgentSessionProvider.from("codex"))

    assertThat(target).isNotNull
    assertThat(target?.projectPath).isEqualTo(PROJECT_PATH)
    assertThat(target?.provider).isEqualTo(AgentSessionProvider.from("codex"))
    assertThat(target?.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.from("codex"), "codex-42"))
    assertThat(target?.threadId).isEqualTo("codex-42")
    assertThat(target?.threadTitle).isEqualTo("Recovered")
  }

  @Test
  fun resolvePrefersConcreteCodexThreadOverProjectedPendingThread() {
    val readService = AgentSessionReadService(
      stateProvider = {
        AgentSessionsState(
          projects = listOf(
            AgentProjectSessions(
              path = PROJECT_PATH,
              name = "Project A",
              isOpen = true,
              providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
              threads = listOf(
                thread(id = "new-9", updatedAt = 900L, provider = AgentSessionProvider.from("codex"), title = "New thread"),
                thread(id = "codex-42", updatedAt = 500L, provider = AgentSessionProvider.from("codex"), title = "Recovered"),
              ),
            )
          )
        )
      },
    )

    val context = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = PROJECT_PATH,
      tabKey = "pending-codex:new-1",
      threadIdentity = "codex:new-1",
      threadCoordinates = AgentChatThreadCoordinates(
        provider = AgentSessionProvider.from("codex"),
        sessionId = "new-1",
        isPending = true,
      ),
    )

    val target = readService.resolvePendingThreadRebindTarget(context, AgentSessionProvider.from("codex"))

    assertThat(target).isNotNull
    assertThat(target?.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.from("codex"), "codex-42"))
    assertThat(target?.threadId).isEqualTo("codex-42")
    assertThat(target?.threadTitle).isEqualTo("Recovered")
  }

  @Test
  fun resolveReturnsNullWhenOnlyProjectedCodexPendingThreadExists() {
    val readService = AgentSessionReadService(
      stateProvider = {
        AgentSessionsState(
          projects = listOf(
            AgentProjectSessions(
              path = PROJECT_PATH,
              name = "Project A",
              isOpen = true,
              providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
              threads = listOf(
                thread(id = "new-9", updatedAt = 900L, provider = AgentSessionProvider.from("codex"), title = "New thread"),
              ),
            )
          )
        )
      },
    )

    val context = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = PROJECT_PATH,
      tabKey = "pending-codex:new-1",
      threadIdentity = "codex:new-1",
      threadCoordinates = AgentChatThreadCoordinates(
        provider = AgentSessionProvider.from("codex"),
        sessionId = "new-1",
        isPending = true,
      ),
    )

    assertThat(readService.resolvePendingThreadRebindTarget(context, AgentSessionProvider.from("codex"))).isNull()
  }

  @Test
  fun resolveDoesNotDependOnLaunchSpecAugmenter() {
    runBlocking(Dispatchers.Default) {
      val readService = AgentSessionReadService(
        stateProvider = {
          AgentSessionsState(
            projects = listOf(
              AgentProjectSessions(
                path = PROJECT_PATH,
                name = "Project A",
                isOpen = true,
                providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
                threads = listOf(thread(id = "codex-42", updatedAt = 500L, provider = AgentSessionProvider.from("codex"), title = "Recovered")),
              )
            )
          )
        },
      )

      val context = AgentChatEditorTabActionContext(
        project = ProjectManager.getInstance().defaultProject,
        path = PROJECT_PATH,
        tabKey = "pending-codex:new-1",
        threadIdentity = "codex:new-1",
        threadCoordinates = AgentChatThreadCoordinates(
          provider = AgentSessionProvider.from("codex"),
          sessionId = "new-1",
          isPending = true,
        ),
      )

      val target = withTestLaunchSpecAugmenter {
        readService.resolvePendingThreadRebindTarget(context, AgentSessionProvider.from("codex"))
      }

      assertThat(target).isNotNull
      assertThat(target?.projectPath).isEqualTo(PROJECT_PATH)
      assertThat(target?.provider).isEqualTo(AgentSessionProvider.from("codex"))
      assertThat(target?.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.from("codex"), "codex-42"))
      assertThat(target?.threadId).isEqualTo("codex-42")
    }
  }

  @Test
  fun resolveSupportsClaudePendingContext() {
    val readService = AgentSessionReadService(
      stateProvider = {
        AgentSessionsState(
          projects = listOf(
            AgentProjectSessions(
              path = PROJECT_PATH,
              name = "Project A",
              isOpen = true,
              providerLoadStates = loadedProviderStates(AgentSessionProvider.from("claude")),
              threads = listOf(
                thread(
                  id = "claude-42",
                  updatedAt = 700L,
                  provider = AgentSessionProvider.from("claude"),
                  title = "Recovered Claude",
                )
              ),
            )
          )
        )
      },
    )

    val context = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = PROJECT_PATH,
      tabKey = "pending-claude:new-1",
      threadIdentity = "claude:new-1",
      threadCoordinates = AgentChatThreadCoordinates(
        provider = AgentSessionProvider.from("claude"),
        sessionId = "new-1",
        isPending = true,
      ),
    )

    val target = readService.resolvePendingThreadRebindTarget(context, AgentSessionProvider.from("claude"))

    assertThat(target).isNotNull
    assertThat(target?.provider).isEqualTo(AgentSessionProvider.from("claude"))
    assertThat(target?.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.from("claude"), "claude-42"))
    assertThat(target?.threadId).isEqualTo("claude-42")
    assertThat(target?.threadTitle).isEqualTo("Recovered Claude")
  }

  @Test
  fun resolvePrefersConcreteClaudeThreadOverProjectedPendingThread() {
    val readService = AgentSessionReadService(
      stateProvider = {
        AgentSessionsState(
          projects = listOf(
            AgentProjectSessions(
              path = PROJECT_PATH,
              name = "Project A",
              isOpen = true,
              providerLoadStates = loadedProviderStates(AgentSessionProvider.from("claude")),
              threads = listOf(
                thread(id = "new-9", updatedAt = 900L, provider = AgentSessionProvider.from("claude"), title = "New thread"),
                thread(id = "claude-42", updatedAt = 700L, provider = AgentSessionProvider.from("claude"), title = "Recovered Claude"),
              ),
            )
          )
        )
      },
    )

    val context = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = PROJECT_PATH,
      tabKey = "pending-claude:new-1",
      threadIdentity = "claude:new-1",
      threadCoordinates = AgentChatThreadCoordinates(
        provider = AgentSessionProvider.from("claude"),
        sessionId = "new-1",
        isPending = true,
      ),
    )

    val target = readService.resolvePendingThreadRebindTarget(context, AgentSessionProvider.from("claude"))

    assertThat(target).isNotNull
    assertThat(target?.threadIdentity).isEqualTo(buildAgentSessionIdentity(AgentSessionProvider.from("claude"), "claude-42"))
    assertThat(target?.threadId).isEqualTo("claude-42")
    assertThat(target?.threadTitle).isEqualTo("Recovered Claude")
  }

  @Test
  fun resolveSkipsPendingContextThatNoLongerParticipatesInPendingLifecycle() {
    val readService = AgentSessionReadService(
      stateProvider = {
        AgentSessionsState(
          projects = listOf(
            AgentProjectSessions(
              path = PROJECT_PATH,
              name = "Project A",
              isOpen = true,
              providerLoadStates = loadedProviderStates(AgentSessionProvider.from("codex")),
              threads = listOf(thread(id = "codex-42", updatedAt = 500L, provider = AgentSessionProvider.from("codex"), title = "Recovered")),
            )
          )
        )
      },
    )

    val context = AgentChatEditorTabActionContext(
      project = ProjectManager.getInstance().defaultProject,
      path = PROJECT_PATH,
      tabKey = "pending-codex:new-1",
      threadIdentity = "codex:new-1",
      threadCoordinates = AgentChatThreadCoordinates(
        provider = AgentSessionProvider.from("codex"),
        sessionId = "new-1",
        isPending = true,
        participatesInPendingThreadLifecycle = false,
      ),
    )

    assertThat(readService.resolvePendingThreadRebindTarget(context, AgentSessionProvider.from("codex"))).isNull()
  }
}
