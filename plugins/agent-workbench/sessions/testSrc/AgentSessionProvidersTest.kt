// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.common.icons.AgentWorkbenchCommonIcons
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.platform.ai.agent.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.ui.agentSessionThreadStatusIcon
import com.intellij.agent.workbench.ui.clearAgentSessionThreadStatusIconCacheForTests
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import javax.swing.Icon

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionProvidersTest {
  @Test
  fun registeringAndDisposingProviderUpdatesFacadeView() {
    val provider = AgentSessionProvider.from("dynamic-provider")
    val initialDescriptor = AgentSessionProviders.find(provider)
    val initialSources = AgentSessionProviders.sessionSources()

    val descriptor = TestAgentSessionProviderDescriptor(
      provider = provider,
      sourceId = "dynamic",
      supportedModes = emptySet(),
      cliAvailable = true,
    )
    val registry = InMemoryAgentSessionProviderRegistry(listOf(descriptor))
    AgentSessionProviders.withRegistryForTest(registry) {
      assertThat(initialDescriptor).isNull()

      val descriptorAfterRegister = AgentSessionProviders.find(provider)
      assertThat(descriptorAfterRegister).isSameAs(descriptor)

      val sourcesAfterRegister = AgentSessionProviders.sessionSources()
      assertThat(sourcesAfterRegister).containsExactly(descriptor.sessionSource)
    }

    assertThat(AgentSessionProviders.find(provider)).isSameAs(initialDescriptor)
    assertThat(AgentSessionProviders.sessionSources()).containsExactlyElementsOf(initialSources)
  }

  @Test
  fun duplicateProvidersKeepFirstDescriptor() {
    val provider = AgentSessionProvider.from("duplicate-provider")

    val firstDescriptor =
      TestAgentSessionProviderDescriptor(provider = provider, sourceId = "first", supportedModes = emptySet(), cliAvailable = true)
    val secondDescriptor =
      TestAgentSessionProviderDescriptor(provider = provider, sourceId = "second", supportedModes = emptySet(), cliAvailable = true)
    val registry = InMemoryAgentSessionProviderRegistry(listOf(firstDescriptor, secondDescriptor))
    AgentSessionProviders.withRegistryForTest(registry) {
      assertThat(AgentSessionProviders.find(provider)).isSameAs(firstDescriptor)
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
      val registry = InMemoryAgentSessionProviderRegistry(
        listOf(lowPriorityDescriptor, alphaDescriptor, betaDescriptor, highPriorityDescriptor),
      )

      AgentSessionProviders.withRegistryForTest(registry) {
        assertThat(AgentSessionProviders.allProviders().map { it.provider.value })
          .containsExactly(
            highPriorityDescriptor.provider.value,
            alphaDescriptor.provider.value,
            betaDescriptor.provider.value,
            lowPriorityDescriptor.provider.value,
          )
        assertThat(AgentSessionProviders.allProvidersById().map { it.provider.value })
          .containsExactly(
            alphaDescriptor.provider.value,
            betaDescriptor.provider.value,
            highPriorityDescriptor.provider.value,
            lowPriorityDescriptor.provider.value,
          )
      }
    }
  }

  @Test
  fun registryCanBeOverriddenForIsolatedTests() {
    val codexDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      sourceId = "override-codex",
      supportedModes = emptySet(),
      cliAvailable = true,
    )
    val claudeDescriptor = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("claude"),
      sourceId = "override-claude",
      supportedModes = emptySet(),
      cliAvailable = true,
    )
    val overrideRegistry = InMemoryAgentSessionProviderRegistry(listOf(claudeDescriptor, codexDescriptor))
    val baselineClaude = AgentSessionProviders.find(AgentSessionProvider.from("claude"))

    AgentSessionProviders.withRegistryForTest(overrideRegistry) {
      assertThat(AgentSessionProviders.find(AgentSessionProvider.from("claude"))).isSameAs(claudeDescriptor)
      assertThat(AgentSessionProviders.allProviders()).containsExactly(claudeDescriptor, codexDescriptor)
      assertThat(AgentSessionProviders.allProvidersById()).containsExactly(claudeDescriptor, codexDescriptor)
      assertThat(AgentSessionProviders.sessionSources())
        .containsExactly(claudeDescriptor.sessionSource, codexDescriptor.sessionSource)
    }

    assertThat(AgentSessionProviders.find(AgentSessionProvider.from("claude"))).isSameAs(baselineClaude)
  }

  @Test
  fun menuModelOmitsUnavailableDiscoverableProviders() {
    val prominentProvider = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("codex"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
    )
    val discoverableProvider = TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("pi"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = false,
      cliVisibilityPolicy = AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE,
    )

    val menuModel = buildAgentSessionProviderMenuModel(
      bridges = listOf(prominentProvider, discoverableProvider),
      availabilityByProvider = mapOf(
        AgentSessionProvider.from("codex") to false,
        AgentSessionProvider.from("pi") to false,
      ),
    )

    assertThat(menuModel.standardItems.map { item -> item.bridge.provider }).containsExactly(AgentSessionProvider.from("codex"))
    assertThat(menuModel.standardItems.single().isEnabled).isFalse()
  }

  @Test
  fun allRegisteredProvidersProvideIcon() {
    AgentSessionProviders.allProviders().forEach { descriptor ->
      assertThat(descriptor.icon).isNotNull()
    }
  }

  @Test
  @RegistryKey(key = "agent.workbench.use.monochrome.icons", value = "true")
  fun providerItemMonochromeIconWithModeUsesMonochromeWhenEnabled() {
    val coloredIcon = AgentWorkbenchCommonIcons.Claude
    val monochromeIcon = AgentWorkbenchCommonIcons.Codex
    val descriptor = object : TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("claude"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      iconOverride = coloredIcon,
    ) {
      override val monochromeIcon: Icon = monochromeIcon
    }
    val item = AgentSessionProviderMenuItem(
      bridge = descriptor,
      mode = AgentSessionLaunchMode.STANDARD,
      labelKey = "some.key",
      isEnabled = true
    )
    assertThat(providerItemMonochromeIconWithMode(item)).isSameAs(monochromeIcon)
  }

  @Test
  @RegistryKey(key = "agent.workbench.use.monochrome.icons", value = "false")
  fun providerItemMonochromeIconWithModeUsesColoredWhenDisabled() {
    val coloredIcon = AgentWorkbenchCommonIcons.Claude
    val monochromeIcon = AgentWorkbenchCommonIcons.Codex
    val descriptor = object : TestAgentSessionProviderDescriptor(
      provider = AgentSessionProvider.from("claude"),
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      iconOverride = coloredIcon,
    ) {
      override val monochromeIcon: Icon = monochromeIcon
    }
    val item = AgentSessionProviderMenuItem(
      bridge = descriptor,
      mode = AgentSessionLaunchMode.STANDARD,
      labelKey = "some.key",
      isEnabled = true
    )
    assertThat(providerItemMonochromeIconWithMode(item)).isSameAs(coloredIcon)
  }

  @Test
  @RegistryKey(key = "agent.workbench.use.monochrome.icons", value = "true")
  fun threadStatusIconCacheSeparatesRegistryModes() {
    val disposable = Disposer.newDisposable()
    disposable.use {
      clearAgentSessionThreadStatusIconCacheForTests()
      Disposer.register(disposable) { clearAgentSessionThreadStatusIconCacheForTests() }

      val coloredIcon = AgentWorkbenchCommonIcons.Claude
      val monochromeIcon = AgentWorkbenchCommonIcons.Codex
      val descriptor = object : TestAgentSessionProviderDescriptor(
        provider = AgentSessionProvider.from("claude"),
        supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
        cliAvailable = true,
        iconOverride = coloredIcon,
      ) {
        override val monochromeIcon: Icon = monochromeIcon
      }
      val overrideRegistry = InMemoryAgentSessionProviderRegistry(listOf(descriptor))

      AgentSessionProviders.withRegistryForTest(overrideRegistry) {
        val registryValue = Registry.get("agent.workbench.use.monochrome.icons")

        val monochromeStatusIcon = agentSessionThreadStatusIcon(AgentSessionProvider.from("claude"), AgentThreadActivity.READY)

        registryValue.setValue(false, disposable)
        val coloredStatusIcon = agentSessionThreadStatusIcon(AgentSessionProvider.from("claude"), AgentThreadActivity.READY)

        assertThat(coloredStatusIcon).isNotSameAs(monochromeStatusIcon)
        assertThat(monochromeStatusIcon).isSameAs(monochromeIcon)
        assertThat(coloredStatusIcon).isSameAs(coloredIcon)
      }
    }
  }
}
