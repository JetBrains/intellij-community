// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderIcon
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentThreadQuickStartServiceImplTest {
  @Test
  fun usesLastUsedProviderWhenEligible() {
    val project = ProjectManager.getInstance().defaultProject
    val codexBridge = TestProviderBridge(provider = AgentSessionProvider.CODEX)
    val claudeBridge = TestProviderBridge(provider = AgentSessionProvider.CLAUDE)
    val bridgeByProvider = mapOf(
      AgentSessionProvider.CODEX to codexBridge,
      AgentSessionProvider.CLAUDE to claudeBridge,
    )

    var launchedPath: String? = null
    var launchedProvider: AgentSessionProvider? = null
    var launchedProject: Project? = null

    val service = AgentThreadQuickStartServiceImpl(
      isDedicatedProject = { true },
      getLastUsedProvider = { AgentSessionProvider.CLAUDE },
      findBridge = { provider -> bridgeByProvider[provider] },
      allBridges = { listOf(codexBridge, claudeBridge) },
      createNewSession = { path, provider, currentProject ->
        launchedPath = path
        launchedProvider = provider
        launchedProject = currentProject
      },
    )

    assertThat(service.isVisible(project)).isTrue()
    assertThat(service.isEnabled(project)).isTrue()

    service.startNewThread(path = "/tmp/project", project = project)
    assertThat(launchedPath).isEqualTo("/tmp/project")
    assertThat(launchedProvider).isEqualTo(AgentSessionProvider.CLAUDE)
    assertThat(launchedProject).isSameAs(project)
  }

  @Test
  fun fallsBackToFirstEligibleProviderWhenLastUsedIsUnavailable() {
    val project = ProjectManager.getInstance().defaultProject
    val claudeBridge = TestProviderBridge(
      provider = AgentSessionProvider.CLAUDE,
      isCliAvailable = false,
    )
    val codexYoloOnlyBridge = TestProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedLaunchModes = setOf(AgentSessionLaunchMode.YOLO),
    )
    val fallbackProvider = AgentSessionProvider.from("fallback")
    val fallbackBridge = TestProviderBridge(provider = fallbackProvider)
    val bridgeByProvider = mapOf(
      AgentSessionProvider.CLAUDE to claudeBridge,
      AgentSessionProvider.CODEX to codexYoloOnlyBridge,
      fallbackProvider to fallbackBridge,
    )

    var launchedProvider: AgentSessionProvider? = null
    val service = AgentThreadQuickStartServiceImpl(
      isDedicatedProject = { true },
      getLastUsedProvider = { AgentSessionProvider.CLAUDE },
      findBridge = { provider -> bridgeByProvider[provider] },
      allBridges = { listOf(codexYoloOnlyBridge, fallbackBridge, claudeBridge) },
      createNewSession = { _, provider, _ -> launchedProvider = provider },
    )

    assertThat(service.isEnabled(project)).isTrue()

    service.startNewThread(path = "/tmp/project", project = project)
    assertThat(launchedProvider).isEqualTo(fallbackProvider)
  }

  @Test
  fun quickStartIsDisabledWhenNoEligibleProviderExists() {
    val project = ProjectManager.getInstance().defaultProject
    val codexYoloOnlyBridge = TestProviderBridge(
      provider = AgentSessionProvider.CODEX,
      supportedLaunchModes = setOf(AgentSessionLaunchMode.YOLO),
    )
    val claudeMissingCliBridge = TestProviderBridge(
      provider = AgentSessionProvider.CLAUDE,
      isCliAvailable = false,
    )

    var launchCount = 0
    val service = AgentThreadQuickStartServiceImpl(
      isDedicatedProject = { true },
      getLastUsedProvider = { AgentSessionProvider.CLAUDE },
      findBridge = {
        when (it) {
          AgentSessionProvider.CODEX -> codexYoloOnlyBridge
          AgentSessionProvider.CLAUDE -> claudeMissingCliBridge
          else -> null
        }
      },
      allBridges = { listOf(codexYoloOnlyBridge, claudeMissingCliBridge) },
      createNewSession = { _, _, _ -> launchCount++ },
    )

    assertThat(service.isEnabled(project)).isFalse()

    service.startNewThread(path = "/tmp/project", project = project)
    assertThat(launchCount).isZero()
  }

  @Test
  fun quickStartIsHiddenOutsideDedicatedFrame() {
    val project = ProjectManager.getInstance().defaultProject
    var launchCount = 0
    val service = AgentThreadQuickStartServiceImpl(
      isDedicatedProject = { false },
      getLastUsedProvider = { AgentSessionProvider.CODEX },
      findBridge = { TestProviderBridge(provider = AgentSessionProvider.CODEX) },
      allBridges = { listOf(TestProviderBridge(provider = AgentSessionProvider.CODEX)) },
      createNewSession = { _, _, _ -> launchCount++ },
    )

    assertThat(service.isVisible(project)).isFalse()
    assertThat(service.isEnabled(project)).isFalse()

    service.startNewThread(path = "/tmp/project", project = project)
    assertThat(launchCount).isZero()
  }
}

private class TestProviderBridge(
  override val provider: AgentSessionProvider,
  private val isCliAvailable: Boolean = true,
  override val supportedLaunchModes: Set<AgentSessionLaunchMode> = setOf(AgentSessionLaunchMode.STANDARD),
) : AgentSessionProviderBridge {
  override val displayNameKey: String
    get() = "toolwindow.provider.codex"

  override val newSessionLabelKey: String
    get() = "toolwindow.action.new.session.codex"

  override val icon: AgentSessionProviderIcon
    get() = AgentSessionProviderIcon(path = "icons/codex@14x14.svg", iconClass = this::class.java)

  override val sessionSource: AgentSessionSource = object : AgentSessionSource {
    override val provider: AgentSessionProvider = this@TestProviderBridge.provider

    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()
  }

  override val cliMissingMessageKey: String
    get() = "toolwindow.action.new.session.unavailable"

  override fun isCliAvailable(): Boolean = isCliAvailable

  override fun buildResumeCommand(sessionId: String): List<String> = listOf("test", "resume", sessionId)

  override fun buildNewSessionCommand(mode: AgentSessionLaunchMode): List<String> = listOf("test", "new", mode.name)

  override fun buildNewEntryCommand(): List<String> = listOf("test")

  override suspend fun createNewSession(path: String, mode: AgentSessionLaunchMode): AgentSessionLaunchSpec {
    return AgentSessionLaunchSpec(
      sessionId = null,
      command = listOf("test", "create", path),
    )
  }
}
