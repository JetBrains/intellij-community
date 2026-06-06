// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionProviderAvailabilityServiceTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val project: Project
    get() = ProjectManager.getInstance().defaultProject

  private val service: AgentSessionProviderAvailabilityService
    get() = project.service()

  @BeforeEach
  fun clearProviderAvailabilityCache() {
    service.clearAvailabilityForTest()
  }

  @Test
  fun refreshNowRunsProviderProbeOffEdtWhenCalledFromEdt() {
    val probeRanOnEdt = AtomicReference<Boolean>()
    val provider = availabilityProvider(AgentSessionProvider.from("test-edt")) {
      probeRanOnEdt.set(ApplicationManager.getApplication().isDispatchThread)
      true
    }

    runInEdtAndWait {
      runBlocking {
        service.refreshNow(listOf(provider))
      }
    }

    assertThat(probeRanOnEdt.get()).isFalse()
    assertThat(service.availabilitySnapshot(listOf(provider))).containsEntry(provider.provider, true)
  }

  @Test
  fun concurrentRefreshesMergeIntoLatestCache() {
    runBlocking {
      val releaseRefreshes = CompletableDeferred<Unit>()
      val firstStarted = CompletableDeferred<Unit>()
      val secondStarted = CompletableDeferred<Unit>()
      val firstProvider = availabilityProvider(AgentSessionProvider.from("test-concurrent-a")) {
        firstStarted.complete(Unit)
        releaseRefreshes.await()
        true
      }
      val secondProvider = availabilityProvider(AgentSessionProvider.from("test-concurrent-b")) {
        secondStarted.complete(Unit)
        releaseRefreshes.await()
        false
      }

      val firstRefresh = async { service.refreshNow(listOf(firstProvider)) }
      val secondRefresh = async { service.refreshNow(listOf(secondProvider)) }
      withTimeout(5.seconds) {
        firstStarted.await()
        secondStarted.await()
      }
      releaseRefreshes.complete(Unit)
      firstRefresh.await()
      secondRefresh.await()

      assertThat(service.availabilitySnapshot(listOf(firstProvider, secondProvider)))
        .containsEntry(firstProvider.provider, true)
        .containsEntry(secondProvider.provider, false)
    }
  }

  @Test
  fun refreshNowProbesProvidersInParallel() {
    runBlocking {
      val releaseFirstProbe = CompletableDeferred<Unit>()
      val firstStarted = CompletableDeferred<Unit>()
      val secondStarted = CompletableDeferred<Unit>()
      val firstProvider = availabilityProvider(AgentSessionProvider.from("test-parallel-a")) {
        firstStarted.complete(Unit)
        releaseFirstProbe.await()
        true
      }
      val secondProvider = availabilityProvider(AgentSessionProvider.from("test-parallel-b")) {
        secondStarted.complete(Unit)
        true
      }

      val refresh = async { service.refreshNow(listOf(firstProvider, secondProvider)) }

      withTimeout(5.seconds) {
        firstStarted.await()
        secondStarted.await()
      }
      releaseFirstProbe.complete(Unit)
      assertThat(refresh.await())
        .containsEntry(firstProvider.provider, true)
        .containsEntry(secondProvider.provider, true)
    }
  }

  @Test
  fun discoverableProviderIsHiddenBeforeProbeAndDoesNotExpireForPassiveRefresh() {
    runBlocking {
      val providerId = AgentSessionProvider.from("test-discoverable")
      val probeCount = AtomicInteger()
      var cliAvailable = false
      val provider = availabilityProvider(
        provider = providerId,
        cliVisibilityPolicy = AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE,
      ) {
        probeCount.incrementAndGet()
        cliAvailable
      }

      assertThat(service.availabilitySnapshot(listOf(provider))).containsEntry(providerId, false)

      service.requestRefresh(listOf(provider))
      waitForCondition { probeCount.get() == 1 }
      assertThat(service.availabilitySnapshot(listOf(provider))).containsEntry(providerId, false)

      cliAvailable = true

      assertThat(service.refreshNow(listOf(provider), force = false)).containsEntry(providerId, false)
      assertThat(probeCount.get()).isEqualTo(1)

      assertThat(service.refreshNow(listOf(provider), force = true)).containsEntry(providerId, true)
      assertThat(probeCount.get()).isEqualTo(2)
    }
  }

  @Test
  fun passiveRefreshCoalescesConcurrentProviderProbe() {
    runBlocking {
      val providerId = AgentSessionProvider.from("test-coalesced")
      val probeCount = AtomicInteger()
      val probeStarted = CompletableDeferred<Unit>()
      val releaseProbe = CompletableDeferred<Unit>()
      val provider = availabilityProvider(
        provider = providerId,
        cliVisibilityPolicy = AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE,
      ) {
        probeCount.incrementAndGet()
        probeStarted.complete(Unit)
        releaseProbe.await()
        true
      }

      val firstRefresh = async { service.refreshNow(listOf(provider), force = false) }
      withTimeout(5.seconds) { probeStarted.await() }

      val secondRefresh = async { service.refreshNow(listOf(provider), force = false) }
      delay(50.milliseconds)

      assertThat(probeCount.get()).isEqualTo(1)
      releaseProbe.complete(Unit)
      assertThat(firstRefresh.await()).containsEntry(providerId, true)
      assertThat(secondRefresh.await()).containsEntry(providerId, true)
      assertThat(probeCount.get()).isEqualTo(1)
    }
  }

  @Test
  fun requestRefreshDrainsProvidersQueuedDuringActiveRefresh() {
    runBlocking {
      val firstStarted = CompletableDeferred<Unit>()
      val releaseFirst = CompletableDeferred<Unit>()
      val secondStarted = CompletableDeferred<Unit>()
      val firstProvider = availabilityProvider(AgentSessionProvider.from("test-queued-a")) {
        firstStarted.complete(Unit)
        releaseFirst.await()
        true
      }
      val secondProvider = availabilityProvider(AgentSessionProvider.from("test-queued-b")) {
        secondStarted.complete(Unit)
        true
      }

      service.requestRefresh(listOf(firstProvider), force = true)
      withTimeout(5.seconds) { firstStarted.await() }

      service.requestRefresh(listOf(secondProvider), force = true)
      assertThat(secondStarted.isCompleted).isFalse()

      releaseFirst.complete(Unit)
      withTimeout(5.seconds) { secondStarted.await() }
      waitForCondition {
        service.availabilitySnapshot(listOf(firstProvider, secondProvider)) == mapOf(
          firstProvider.provider to true,
          secondProvider.provider to true,
        )
      }
    }
  }

  @Test
  fun requestRefreshRefreshesStaleUnavailableProvider() {
    runBlocking {
      val provider = availabilityProvider(AgentSessionProvider.from("test-stale")) { true }
      service.setAvailabilityForTest(
        mapOf(provider.provider to false),
        updatedAtMs = System.currentTimeMillis() - AGENT_SESSION_PROVIDER_AVAILABILITY_CACHE_TTL_MS - 1,
      )

      service.requestRefresh(listOf(provider))

      waitForCondition {
        service.availabilitySnapshot(listOf(provider))[provider.provider] == true
      }
    }
  }

  @Test
  fun unchangedRefreshUpdatesTimestampWithoutPublishingAvailabilityChange() {
    runBlocking {
      val provider = availabilityProvider(AgentSessionProvider.from("test-events")) { true }
      service.setAvailabilityForTest(
        mapOf(provider.provider to true),
        updatedAtMs = System.currentTimeMillis() - AGENT_SESSION_PROVIDER_AVAILABILITY_CACHE_TTL_MS - 1,
      )
      val eventCount = AtomicInteger()
      project.messageBus.connect(disposable)
        .subscribe(AgentSessionProviderAvailabilityListener.TOPIC, object : AgentSessionProviderAvailabilityListener {
          override fun availabilityChanged() {
            eventCount.incrementAndGet()
          }
        })

      service.refreshNow(listOf(provider))

      assertThat(eventCount.get()).isZero()
      service.refreshNow(listOf(availabilityProvider(provider.provider) { false }))
      assertThat(eventCount.get()).isEqualTo(1)
    }
  }

  @Test
  fun disabledProvidersAreNotProbed() {
    val providerId = AgentSessionProvider.from("test-disabled")
    val providerSettings = service<AgentSessionProviderSettingsService>()
    val probeCount = AtomicInteger()
    val provider = availabilityProvider(providerId) {
      probeCount.incrementAndGet()
      true
    }

    try {
      providerSettings.setProviderEnabled(providerId, false)

      service.requestRefresh(listOf(provider), force = true)

      assertThat(probeCount.get()).isZero()
      assertThat(service.availabilitySnapshot(listOf(provider))).containsEntry(providerId, false)
    }
    finally {
      providerSettings.setProviderEnabled(providerId, true)
    }
  }

  private suspend fun waitForCondition(condition: () -> Boolean) {
    withTimeout(5.seconds) {
      while (!condition()) {
        delay(10.milliseconds)
      }
    }
  }

  private fun availabilityProvider(
    provider: AgentSessionProvider,
    cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy = AgentSessionProviderCliVisibilityPolicy.PROMINENT,
    availability: suspend () -> Boolean,
  ): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider = provider
      override val cliVisibilityPolicy: AgentSessionProviderCliVisibilityPolicy = cliVisibilityPolicy
      override val displayNameKey: String = "provider.${provider.value}"
      override val newSessionLabelKey: String = displayNameKey
      override val icon = EmptyIcon.ICON_16
      override val sessionSource: AgentSessionSource
        get() = error("Not required for this test")
      override val cliMissingMessageKey: String = displayNameKey

      override suspend fun isCliAvailable(): Boolean = availability()

      override suspend fun buildResumeLaunchSpec(sessionId: String): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override suspend fun buildNewSessionLaunchSpec(mode: AgentSessionLaunchMode): AgentSessionTerminalLaunchSpec {
        return AgentSessionTerminalLaunchSpec(command = emptyList())
      }

      override fun buildInitialMessagePlan(request: AgentPromptInitialMessageRequest): AgentInitialMessagePlan {
        return AgentInitialMessagePlan.EMPTY
      }
    }
  }
}
