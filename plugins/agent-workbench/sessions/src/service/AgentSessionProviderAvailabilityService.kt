// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

private class AgentSessionProviderAvailabilityServiceLog

private val LOG = logger<AgentSessionProviderAvailabilityServiceLog>()

internal const val AGENT_SESSION_PROVIDER_AVAILABILITY_CACHE_TTL_MS: Long = 30_000

interface AgentSessionProviderAvailabilityListener {
  fun availabilityChanged()

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<AgentSessionProviderAvailabilityListener> = Topic.create(
      "Agent Session Provider Availability",
      AgentSessionProviderAvailabilityListener::class.java,
    )
  }
}

@Service(Service.Level.PROJECT)
class AgentSessionProviderAvailabilityService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  private val lock = Any()

  @Volatile
  private var cachedAvailability: Map<AgentSessionProvider, ProviderAvailabilityCacheEntry> = emptyMap()
  private val pendingRefreshProviders = LinkedHashMap<AgentSessionProvider, AgentSessionProviderDescriptor>()
  private var refreshInProgress = false

  fun availabilitySnapshot(
    providers: List<AgentSessionProviderDescriptor> = AgentSessionProviders.allProviders(),
  ): Map<AgentSessionProvider, Boolean> {
    val snapshot = cachedAvailability
    return providers.associate { provider -> provider.provider to (snapshot[provider.provider]?.available ?: true) }
  }

  fun isProviderAvailable(provider: AgentSessionProvider): Boolean {
    return cachedAvailability[provider]?.available ?: true
  }

  fun requestRefresh(
    providers: List<AgentSessionProviderDescriptor> = AgentSessionProviders.allProviders(),
    force: Boolean = false,
  ) {
    if (project.isDisposed) return
    val providersToRefresh = providersNeedingRefresh(providers, force = force)
    if (providersToRefresh.isEmpty()) return
    val shouldLaunch = synchronized(lock) {
      providersToRefresh.forEach { provider -> pendingRefreshProviders[provider.provider] = provider }
      if (refreshInProgress) {
        false
      }
      else {
        refreshInProgress = true
        true
      }
    }
    if (!shouldLaunch) return

    coroutineScope.launch(Dispatchers.Default) {
      drainRefreshQueue()
    }
  }

  suspend fun refreshNow(
    providers: List<AgentSessionProviderDescriptor> = AgentSessionProviders.allProviders(),
  ): Map<AgentSessionProvider, Boolean> {
    if (project.isDisposed) return emptyMap()
    val resolvedAvailability = withContext(Dispatchers.Default) {
      providers.associate { provider -> provider.provider to resolveAvailability(provider) }
    }
    updateAvailability(resolvedAvailability, updatedAtMs = System.currentTimeMillis())
    return availabilitySnapshot(providers)
  }

  @TestOnly
  fun setAvailabilityForTest(
    availability: Map<AgentSessionProvider, Boolean>,
    updatedAtMs: Long = System.currentTimeMillis(),
  ) {
    replaceAvailability(availability.mapValues { (_, available) -> ProviderAvailabilityCacheEntry(available, updatedAtMs) })
  }

  @TestOnly
  fun clearAvailabilityForTest() {
    synchronized(lock) {
      pendingRefreshProviders.clear()
    }
    replaceAvailability(emptyMap())
  }

  private suspend fun drainRefreshQueue() {
    while (true) {
      val providers = synchronized(lock) {
        if (pendingRefreshProviders.isEmpty()) {
          refreshInProgress = false
          return
        }
        pendingRefreshProviders.values.toList().also {
          pendingRefreshProviders.clear()
        }
      }
      refreshNow(providers)
    }
  }

  private fun providersNeedingRefresh(
    providers: List<AgentSessionProviderDescriptor>,
    force: Boolean,
  ): List<AgentSessionProviderDescriptor> {
    if (force) return providers
    val now = System.currentTimeMillis()
    val snapshot = cachedAvailability
    return providers.filter { provider -> snapshot[provider.provider]?.isFresh(now) != true }
  }

  private suspend fun resolveAvailability(provider: AgentSessionProviderDescriptor): Boolean {
    return try {
      provider.isCliAvailable()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (t: Throwable) {
      LOG.warn("Failed to resolve CLI availability for ${provider.provider.value}", t)
      false
    }
  }

  private fun updateAvailability(
    availability: Map<AgentSessionProvider, Boolean>,
    updatedAtMs: Long,
  ) {
    val updated = availability.mapValues { (_, available) -> ProviderAvailabilityCacheEntry(available, updatedAtMs) }
    var shouldPublish = false
    synchronized(lock) {
      val mergedAvailability = cachedAvailability + updated
      if (cachedAvailability != mergedAvailability) {
        shouldPublish = effectiveAvailabilityChanged(cachedAvailability, mergedAvailability)
        cachedAvailability = mergedAvailability
      }
    }
    if (shouldPublish) {
      publishAvailabilityChanged()
    }
  }

  private fun replaceAvailability(availability: Map<AgentSessionProvider, ProviderAvailabilityCacheEntry>) {
    var shouldPublish = false
    synchronized(lock) {
      if (cachedAvailability != availability) {
        shouldPublish = effectiveAvailabilityChanged(cachedAvailability, availability)
        cachedAvailability = availability
      }
    }
    if (shouldPublish) {
      publishAvailabilityChanged()
    }
  }

  private fun effectiveAvailabilityChanged(
    previous: Map<AgentSessionProvider, ProviderAvailabilityCacheEntry>,
    updated: Map<AgentSessionProvider, ProviderAvailabilityCacheEntry>,
  ): Boolean {
    return (previous.keys + updated.keys).any { provider ->
      (previous[provider]?.available ?: true) != (updated[provider]?.available ?: true)
    }
  }

  private fun publishAvailabilityChanged() {
    ActivityTracker.getInstance().inc()
    project.messageBus.syncPublisher(AgentSessionProviderAvailabilityListener.TOPIC).availabilityChanged()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): AgentSessionProviderAvailabilityService = project.service()
  }
}

private data class ProviderAvailabilityCacheEntry(
  @JvmField val available: Boolean,
  @JvmField val updatedAtMs: Long,
) {
  fun isFresh(nowMs: Long): Boolean {
    return nowMs - updatedAtMs <= AGENT_SESSION_PROVIDER_AVAILABILITY_CACHE_TTL_MS
  }
}
