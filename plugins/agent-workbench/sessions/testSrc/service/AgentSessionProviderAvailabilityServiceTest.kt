// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.agent.workbench.sessions.core.providers.AgentInitialMessagePlan
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class AgentSessionProviderAvailabilityServiceTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val project: Project
    get() = ProjectManager.getInstance().defaultProject

  private val service: AgentSessionProviderAvailabilityService
    get() = AgentSessionProviderAvailabilityService.getInstance(project)

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

  private suspend fun waitForCondition(condition: () -> Boolean) {
    withTimeout(5.seconds) {
      while (!condition()) {
        delay(10.milliseconds)
      }
    }
  }

  private fun availabilityProvider(
    provider: AgentSessionProvider,
    availability: suspend () -> Boolean,
  ): AgentSessionProviderDescriptor {
    return object : AgentSessionProviderDescriptor {
      override val provider: AgentSessionProvider = provider
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
