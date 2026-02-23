// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionProviderIconIds
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionLaunchSpec
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridges
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionProviderBridgesTest {
  private val extensionPoint =
    ExtensionPointName<AgentSessionProviderBridge>("com.intellij.agent.workbench.sessionProviderBridge")

  @Test
  fun registeringAndDisposingBridgeUpdatesFacadeView() {
    val provider = AgentSessionProvider.CODEX
    val initialBridge = AgentSessionProviderBridges.find(provider)
    val initialSources = AgentSessionProviderBridges.sessionSources()

    val disposable = Disposer.newDisposable()
    try {
      val bridge = TestAgentSessionProviderBridge(provider = provider, sourceId = "dynamic")
      extensionPoint.point.registerExtension(bridge, disposable)

      val bridgeAfterRegister = AgentSessionProviderBridges.find(provider)
      val expectedBridge = initialBridge ?: bridge
      assertThat(bridgeAfterRegister).isSameAs(expectedBridge)

      val sourcesAfterRegister = AgentSessionProviderBridges.sessionSources()
      if (initialBridge == null) {
        assertThat(sourcesAfterRegister).hasSize(initialSources.size + 1)
        assertThat(sourcesAfterRegister).contains(bridge.sessionSource)
      }
      else {
        assertThat(sourcesAfterRegister).containsExactlyElementsOf(initialSources)
      }
    }
    finally {
      Disposer.dispose(disposable)
    }

    assertThat(AgentSessionProviderBridges.find(provider)).isSameAs(initialBridge)
    assertThat(AgentSessionProviderBridges.sessionSources()).containsExactlyElementsOf(initialSources)
  }

  @Test
  fun duplicateBridgesKeepFirstBridge() {
    val provider = AgentSessionProvider.CODEX
    val initialBridge = AgentSessionProviderBridges.find(provider)

    val disposable = Disposer.newDisposable()
    try {
      val firstBridge = TestAgentSessionProviderBridge(provider = provider, sourceId = "first")
      val secondBridge = TestAgentSessionProviderBridge(provider = provider, sourceId = "second")
      extensionPoint.point.registerExtension(firstBridge, disposable)
      extensionPoint.point.registerExtension(secondBridge, disposable)

      val expectedBridge = initialBridge ?: firstBridge
      assertThat(AgentSessionProviderBridges.find(provider)).isSameAs(expectedBridge)
      assertThat(AgentSessionProviderBridges.find(provider)).isNotSameAs(secondBridge)
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun allBridgesFollowExtensionOrder() {
    val disposable = Disposer.newDisposable()
    try {
      val lastBridge = TestAgentSessionProviderBridge(provider = AgentSessionProvider.from("aaa"), sourceId = "last")
      val firstBridge = TestAgentSessionProviderBridge(provider = AgentSessionProvider.from("bbb"), sourceId = "first")
      extensionPoint.point.registerExtension(lastBridge, LoadingOrder.LAST, disposable)
      extensionPoint.point.registerExtension(firstBridge, LoadingOrder.FIRST, disposable)

      val orderedIds = AgentSessionProviderBridges.allBridges().map { it.provider.value }
      assertThat(orderedIds.indexOf(firstBridge.provider.value)).isLessThan(orderedIds.indexOf(lastBridge.provider.value))
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun registryCanBeOverriddenForIsolatedTests() {
    val codexBridge = TestAgentSessionProviderBridge(provider = AgentSessionProvider.CODEX, sourceId = "override-codex")
    val claudeBridge = TestAgentSessionProviderBridge(provider = AgentSessionProvider.CLAUDE, sourceId = "override-claude")
    val overrideRegistry = InMemoryAgentSessionProviderRegistry(listOf(claudeBridge, codexBridge))
    val baselineClaude = AgentSessionProviderBridges.find(AgentSessionProvider.CLAUDE)

    AgentSessionProviderBridges.withRegistryForTest(overrideRegistry) {
      assertThat(AgentSessionProviderBridges.find(AgentSessionProvider.CLAUDE)).isSameAs(claudeBridge)
      assertThat(AgentSessionProviderBridges.allBridges()).containsExactly(claudeBridge, codexBridge)
      assertThat(AgentSessionProviderBridges.sessionSources())
        .containsExactly(claudeBridge.sessionSource, codexBridge.sessionSource)
    }

    assertThat(AgentSessionProviderBridges.find(AgentSessionProvider.CLAUDE)).isSameAs(baselineClaude)
  }

  private class TestAgentSessionProviderBridge(
    override val provider: AgentSessionProvider,
    sourceId: String,
  ) : AgentSessionProviderBridge {
    override val sessionSource: AgentSessionSource = TestAgentSessionSource(provider, sourceId)

    override val displayNameKey: String
      get() = "toolwindow.provider.codex"

    override val newSessionLabelKey: String
      get() = "toolwindow.action.new.session.codex"

    override val iconId: String
      get() = AgentSessionProviderIconIds.CODEX

    override val cliMissingMessageKey: String
      get() = "test.missing.cli"

    override fun isCliAvailable(): Boolean = true

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

  private class TestAgentSessionSource(
    override val provider: AgentSessionProvider,
    private val sourceId: String,
  ) : AgentSessionSource {
    override suspend fun listThreadsFromOpenProject(path: String, project: Project): List<AgentSessionThread> = emptyList()

    override suspend fun listThreadsFromClosedProject(path: String): List<AgentSessionThread> = emptyList()

    override fun toString(): String = "TestAgentSessionSource($sourceId)"
  }
}
