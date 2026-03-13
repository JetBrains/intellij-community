// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow

import com.intellij.agent.workbench.sessions.TestAgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.agent.workbench.sessions.model.AgentProjectSessions
import com.intellij.agent.workbench.sessions.model.AgentWorktree
import com.intellij.agent.workbench.sessions.toolwindow.tree.NewSessionRowActions
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.resolveNewSessionRowActions
import com.intellij.agent.workbench.sessions.toolwindow.tree.resolveQuickCreateProvider
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsSwingNewSessionActionsTest {
  @Test
  fun projectAndWorktreeRowsExposeQuickProviderFromLastUsedProvider() {
    val bridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true)
      val worktree = AgentWorktree(
        path = "/work/project-a-feature",
        name = "project-a-feature",
        branch = "feature",
        isOpen = false,
      )

      assertThat(resolveNewSessionRowActions(SessionTreeNode.Project(project), AgentSessionProvider.CODEX))
        .isEqualTo(NewSessionRowActions(path = "/work/project-a", quickProvider = AgentSessionProvider.CODEX))
      assertThat(resolveNewSessionRowActions(SessionTreeNode.Worktree(project, worktree), AgentSessionProvider.CODEX))
        .isEqualTo(NewSessionRowActions(path = "/work/project-a-feature", quickProvider = AgentSessionProvider.CODEX))
    }
  }

  @Test
  fun loadingProjectAndWorktreeRowsExposeNewSessionActions() {
    val bridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(bridge))) {
      val project = AgentProjectSessions(path = "/work/project-a", name = "Project A", isOpen = true, isLoading = true)
      val worktree = AgentWorktree(
        path = "/work/project-a-feature",
        name = "project-a-feature",
        branch = "feature",
        isOpen = false,
        isLoading = true,
      )

      assertThat(resolveNewSessionRowActions(SessionTreeNode.Project(project), AgentSessionProvider.CODEX))
        .isEqualTo(NewSessionRowActions(path = "/work/project-a", quickProvider = AgentSessionProvider.CODEX))
      assertThat(resolveNewSessionRowActions(SessionTreeNode.Worktree(project, worktree), AgentSessionProvider.CODEX))
        .isEqualTo(NewSessionRowActions(path = "/work/project-a-feature", quickProvider = AgentSessionProvider.CODEX))
    }
  }

  @Test
  fun quickProviderFallsBackToFirstStandardProvider() {
    val codexBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
    )
    val claudeYoloOnlyBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      supportedModes = setOf(AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.claude.yolo",
    )

    AgentSessionProviders.withRegistryForTest(
      InMemoryAgentSessionProviderRegistry(listOf(codexBridge, claudeYoloOnlyBridge))
    ) {
      assertThat(resolveQuickCreateProvider(AgentSessionProvider.CODEX)).isEqualTo(AgentSessionProvider.CODEX)
      assertThat(resolveQuickCreateProvider(AgentSessionProvider.CLAUDE)).isEqualTo(AgentSessionProvider.CODEX)
      assertThat(resolveQuickCreateProvider(null)).isEqualTo(AgentSessionProvider.CODEX)
    }

    val yoloOnlyBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("yolo-only"),
      supportedModes = setOf(AgentSessionLaunchMode.YOLO),
      cliAvailable = true,
      yoloSessionLabelKey = "toolwindow.action.new.session.codex.yolo",
    )

    val fallbackStandardBridge = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("fallback"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
    )

    AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(yoloOnlyBridge, fallbackStandardBridge))) {
      assertThat(resolveQuickCreateProvider(AgentSessionProvider.CODEX)).isEqualTo(fallbackStandardBridge.provider)
    }
  }
}
