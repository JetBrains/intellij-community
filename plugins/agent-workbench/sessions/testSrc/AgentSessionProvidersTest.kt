// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionProvidersTest {
  private val extensionPoint =
    ExtensionPointName<AgentSessionProviderDescriptor>("com.intellij.agent.workbench.sessionProvider")

  @Test
  fun registeringAndDisposingProviderUpdatesFacadeView() {
    val provider = AgentSessionProvider.CODEX
    val initialDescriptor = AgentSessionProviders.find(provider)
    val initialSources = AgentSessionProviders.sessionSources()

    val disposable = Disposer.newDisposable()
    disposable.use {
      val descriptor = TestAgentSessionProviderDescriptor(
        provider = provider,
        sourceId = "dynamic",
        supportedModes = emptySet(),
        cliAvailable = true,
      )
      extensionPoint.point.registerExtension(descriptor, disposable)

      val descriptorAfterRegister = AgentSessionProviders.find(provider)
      val expectedDescriptor = initialDescriptor ?: descriptor
      assertThat(descriptorAfterRegister).isSameAs(expectedDescriptor)

      val sourcesAfterRegister = AgentSessionProviders.sessionSources()
      if (initialDescriptor == null) {
        assertThat(sourcesAfterRegister).hasSize(initialSources.size + 1)
        assertThat(sourcesAfterRegister).contains(descriptor.sessionSource)
      }
      else {
        assertThat(sourcesAfterRegister).containsExactlyElementsOf(initialSources)
      }
    }

    assertThat(AgentSessionProviders.find(provider)).isSameAs(initialDescriptor)
    assertThat(AgentSessionProviders.sessionSources()).containsExactlyElementsOf(initialSources)
  }

  @Test
  fun duplicateProvidersKeepFirstDescriptor() {
    val provider = AgentSessionProvider.CODEX
    val initialDescriptor = AgentSessionProviders.find(provider)

    val disposable = Disposer.newDisposable()
    disposable.use {
      val firstDescriptor = TestAgentSessionProviderDescriptor(provider = provider, sourceId = "first", supportedModes = emptySet(), cliAvailable = true)
      val secondDescriptor = TestAgentSessionProviderDescriptor(provider = provider, sourceId = "second", supportedModes = emptySet(), cliAvailable = true)
      extensionPoint.point.registerExtension(firstDescriptor, disposable)
      extensionPoint.point.registerExtension(secondDescriptor, disposable)

      val expectedDescriptor = initialDescriptor ?: firstDescriptor
      assertThat(AgentSessionProviders.find(provider)).isSameAs(expectedDescriptor)
      assertThat(AgentSessionProviders.find(provider)).isNotSameAs(secondDescriptor)
    }
  }

  @Test
  fun allProvidersUseDisplayAndIdOrdering() {
    val disposable = Disposer.newDisposable()
    disposable.use {
      val lowPriorityDescriptor = TestAgentSessionProviderDescriptor(
        provider = AgentSessionProvider.from("zzz-low-priority"),
        sourceId = "low-priority",
        displayPriority = 300,
        supportedModes = emptySet(),
        cliAvailable = true,
      )
      val alphaDescriptor = TestAgentSessionProviderDescriptor(
        provider = AgentSessionProvider.from("alpha-priority"),
        sourceId = "alpha-priority",
        displayPriority = 200,
        supportedModes = emptySet(),
        cliAvailable = true,
      )
      val betaDescriptor = TestAgentSessionProviderDescriptor(
        provider = AgentSessionProvider.from("beta-priority"),
        sourceId = "beta-priority",
        displayPriority = 200,
        supportedModes = emptySet(),
        cliAvailable = true,
      )
      val highPriorityDescriptor = TestAgentSessionProviderDescriptor(
        provider = AgentSessionProvider.from("highest-priority"),
        sourceId = "highest-priority",
        displayPriority = 100,
        supportedModes = emptySet(),
        cliAvailable = true,
      )
      extensionPoint.point.registerExtension(lowPriorityDescriptor, LoadingOrder.FIRST, disposable)
      extensionPoint.point.registerExtension(alphaDescriptor, LoadingOrder.LAST, disposable)
      extensionPoint.point.registerExtension(betaDescriptor, LoadingOrder.FIRST, disposable)
      extensionPoint.point.registerExtension(highPriorityDescriptor, LoadingOrder.LAST, disposable)

      assertThat(AgentSessionProviders.allProviders().map { it.provider.value })
        .containsSubsequence(
          highPriorityDescriptor.provider.value,
          alphaDescriptor.provider.value,
          betaDescriptor.provider.value,
          lowPriorityDescriptor.provider.value,
        )
      assertThat(AgentSessionProviders.allProvidersById().map { it.provider.value })
        .containsSubsequence(
          alphaDescriptor.provider.value,
          betaDescriptor.provider.value,
          highPriorityDescriptor.provider.value,
          lowPriorityDescriptor.provider.value,
        )
    }
  }

  @Test
  fun registryCanBeOverriddenForIsolatedTests() {
    val codexDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CODEX,
      sourceId = "override-codex",
      supportedModes = emptySet(),
      cliAvailable = true,
    )
    val claudeDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.CLAUDE,
      sourceId = "override-claude",
      supportedModes = emptySet(),
      cliAvailable = true,
    )
    val overrideRegistry = InMemoryAgentSessionProviderRegistry(listOf(claudeDescriptor, codexDescriptor))
    val baselineClaude = AgentSessionProviders.find(AgentSessionProvider.CLAUDE)

    AgentSessionProviders.withRegistryForTest(overrideRegistry) {
      assertThat(AgentSessionProviders.find(AgentSessionProvider.CLAUDE)).isSameAs(claudeDescriptor)
      assertThat(AgentSessionProviders.allProviders()).containsExactly(claudeDescriptor, codexDescriptor)
      assertThat(AgentSessionProviders.allProvidersById()).containsExactly(claudeDescriptor, codexDescriptor)
      assertThat(AgentSessionProviders.sessionSources())
        .containsExactly(claudeDescriptor.sessionSource, codexDescriptor.sessionSource)
    }

    assertThat(AgentSessionProviders.find(AgentSessionProvider.CLAUDE)).isSameAs(baselineClaude)
  }

  @Test
  fun allRegisteredProvidersProvideIcon() {
    AgentSessionProviders.allProviders().forEach { descriptor ->
      assertThat(descriptor.icon).isNotNull()
    }
  }
}
