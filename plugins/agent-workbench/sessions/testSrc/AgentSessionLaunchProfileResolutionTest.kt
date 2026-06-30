// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchIntent
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchOperation
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionLaunchPlanner
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaceId
import com.intellij.platform.ai.agent.sessions.core.launch.AgentSessionSurfaces
import com.intellij.platform.ai.agent.sessions.core.providers.InMemoryAgentSessionProviderRegistry
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.resolveAgentSessionLaunchProfile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionLaunchProfileResolutionTest {
  @Test
  fun missingBlankInvalidAndUnsupportedPersistedSurfaceIdsFallBackToDescriptorDefault() {
    val descriptor = descriptor(
      defaultLaunchSurface = AgentSessionSurfaces.ACP,
      supportedLaunchSurfaces = setOf(AgentSessionSurfaces.ACP),
    )

    assertThat(resolveProfile(profile(surfaceId = null), descriptor).surfaceId).isEqualTo(AgentSessionSurfaces.ACP)
    assertThat(resolveProfile(profile(surfaceId = "   "), descriptor).surfaceId).isEqualTo(AgentSessionSurfaces.ACP)
    assertThat(resolveProfile(profile(surfaceId = "future surface"), descriptor).surfaceId).isEqualTo(AgentSessionSurfaces.ACP)
    assertThat(resolveProfile(profile(surfaceId = AgentSessionSurfaces.TERMINAL.value), descriptor).surfaceId).isEqualTo(
      AgentSessionSurfaces.ACP)
  }

  @Test
  fun providerIdAcpDoesNotForceAcpSurface() {
    val descriptor = descriptor(provider = AgentSessionProvider.from("acp"))

    val resolvedProfile = resolveProfile(profile(provider = AgentSessionProvider.from("acp"), surfaceId = null), descriptor)

    assertThat(resolvedProfile.surfaceId).isEqualTo(AgentSessionSurfaces.TERMINAL)
  }

  @Test
  fun acpDescriptorResolvesAcpByDefault() {
    val descriptor = descriptor(
      provider = AgentSessionProvider.from("acp"),
      defaultLaunchSurface = AgentSessionSurfaces.ACP,
      supportedLaunchSurfaces = setOf(AgentSessionSurfaces.ACP),
    )

    val resolvedProfile = resolveProfile(profile(provider = AgentSessionProvider.from("acp"), surfaceId = null), descriptor)

    assertThat(resolvedProfile.surfaceId).isEqualTo(AgentSessionSurfaces.ACP)
  }

  @Test
  fun terminalProvidersDefaultToTerminal() {
    val resolvedProfile = resolveProfile(profile(surfaceId = null), descriptor())

    assertThat(resolvedProfile.surfaceId).isEqualTo(AgentSessionSurfaces.TERMINAL)
  }

  @Test
  fun fakeProviderWithFakeSurfaceResolvesThroughProfileAndLaunchIntent() {
    val fakeProvider = AgentSessionProvider.from("future")
    val fakeSurface = AgentSessionSurfaceId.from("tui")
    val descriptor = descriptor(
      provider = fakeProvider,
      defaultLaunchSurface = fakeSurface,
      supportedLaunchSurfaces = setOf(fakeSurface),
    )

    val resolvedProfile = resolveProfile(profile(provider = fakeProvider, surfaceId = fakeSurface.value), descriptor)
    val plannedLaunch = AgentSessionProviders.withRegistryForTest(InMemoryAgentSessionProviderRegistry(listOf(descriptor))) {
      runBlocking(Dispatchers.Default) {
        AgentSessionLaunchPlanner.plan(
          intent = AgentSessionLaunchIntent(
            projectPath = "/tmp/project",
            provider = fakeProvider,
            operation = AgentSessionLaunchOperation.NEW,
            surfaceId = resolvedProfile.surfaceId,
          )
        )
      }
    }

    assertThat(resolvedProfile.surfaceId).isEqualTo(fakeSurface)
    assertThat(plannedLaunch.intent.surfaceId).isEqualTo(fakeSurface)
  }

  private fun resolveProfile(
    profile: AgentPromptLaunchProfile,
    descriptor: TestAgentSessionProviderDescriptor,
  ) = checkNotNull(
    resolveAgentSessionLaunchProfile(
      launchProfileId = profile.id,
      builtInProfiles = emptyList(),
      userProfiles = listOf(profile),
      providerDescriptors = listOf(descriptor),
    )
  )

  private fun profile(
    provider: AgentSessionProvider = AgentSessionProvider.from("codex"),
    surfaceId: String?,
  ): AgentPromptLaunchProfile {
    return AgentPromptLaunchProfile(
      id = "profile:${provider.value}",
      name = "Profile",
      providerId = provider.value,
      launchMode = AgentSessionLaunchMode.STANDARD,
      surfaceId = surfaceId,
    )
  }

  private fun descriptor(
    provider: AgentSessionProvider = AgentSessionProvider.from("codex"),
    defaultLaunchSurface: AgentSessionSurfaceId = AgentSessionSurfaces.TERMINAL,
    supportedLaunchSurfaces: Set<AgentSessionSurfaceId> = setOf(defaultLaunchSurface),
  ): TestAgentSessionProviderDescriptor {
    return TestAgentSessionProviderDescriptor(
      provider = provider,
      supportedModes = setOf(AgentSessionLaunchMode.STANDARD),
      cliAvailable = true,
      defaultLaunchSurface = defaultLaunchSurface,
      supportedLaunchSurfaces = supportedLaunchSurfaces,
    )
  }
}
