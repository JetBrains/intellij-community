// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderCliVisibilityPolicy
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    val providerSettings = service<AgentSessionProviderSettingsService>()
    return availabilitySnapshot(providers, providerSettings)
  }

  private fun availabilitySnapshot(
    providers: List<AgentSessionProviderDescriptor>,
    providerSettings: AgentSessionProviderSettingsService,
  ): Map<AgentSessionProvider, Boolean> {
    val snapshot = cachedAvailability
    return providers.associate { provider ->
      provider.provider to (providerSettings.isProviderEnabled(provider.provider) && provider.availabilityFrom(snapshot))
    }
  }

  fun isProviderAvailable(provider: AgentSessionProvider): Boolean {
    val descriptor = AgentSessionProviders.find(provider)
    return service<AgentSessionProviderSettingsService>().isProviderEnabled(provider) &&
           (cachedAvailability[provider]?.available ?: descriptor.defaultAvailabilityBeforeRefresh())
  }

  fun requestRefresh(
    providers: List<AgentSessionProviderDescriptor> = AgentSessionProviders.allProviders(),
    force: Boolean = false,
  ) {
    if (project.isDisposed) return
    val enabledProviders = service<AgentSessionProviderSettingsService>().enabledProviders(providers)
    val providersToRefresh = providersNeedingRefresh(enabledProviders, force = force)
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
    force: Boolean = true,
  ): Map<AgentSessionProvider, Boolean> {
    if (project.isDisposed) return emptyMap()
    val providerSettings = serviceAsync<AgentSessionProviderSettingsService>()
    val enabledProviders = providerSettings.enabledProviders(providers)
    val providersToRefresh = providersNeedingRefresh(enabledProviders, force = force)
    if (providersToRefresh.isNotEmpty()) {
      val resolvedAvailability = withContext(Dispatchers.IO) {
        resolveAvailabilityInParallel(providersToRefresh, force = force)
      }
      updateAvailability(
        providers = providers,
        availability = resolvedAvailability,
        updatedAtMs = System.currentTimeMillis(),
      )
    }
    return availabilitySnapshot(providers, providerSettings)
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
    AgentSessionProviderCliAvailabilityCache.clearForTest()
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
      refreshNow(providers, force = false)
    }
  }

  private fun providersNeedingRefresh(
    providers: List<AgentSessionProviderDescriptor>,
    force: Boolean,
  ): List<AgentSessionProviderDescriptor> {
    if (force) return providers
    val now = System.currentTimeMillis()
    val snapshot = cachedAvailability
    return providers.filter { provider -> snapshot[provider.provider]?.isFresh(now, provider) != true }
  }

  private suspend fun resolveAvailabilityInParallel(
    providers: List<AgentSessionProviderDescriptor>,
    force: Boolean,
  ): Map<AgentSessionProvider, Boolean> {
    return coroutineScope {
      providers.map { provider ->
        async { provider.provider to resolveAvailability(provider, force = force) }
      }.awaitAll().toMap()
    }
  }

  private suspend fun resolveAvailability(provider: AgentSessionProviderDescriptor, force: Boolean): Boolean {
    return try {
      AgentSessionProviderCliAvailabilityCache.resolveAvailability(provider, force = force) {
        provider.isCliAvailable()
      }
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
    providers: List<AgentSessionProviderDescriptor>,
    availability: Map<AgentSessionProvider, Boolean>,
    updatedAtMs: Long,
  ) {
    val updated = availability.mapValues { (_, available) -> ProviderAvailabilityCacheEntry(available, updatedAtMs) }
    val descriptorsByProvider = providers.associateBy { provider -> provider.provider }
    var shouldPublish = false
    synchronized(lock) {
      val mergedAvailability = cachedAvailability + updated
      if (cachedAvailability != mergedAvailability) {
        shouldPublish = effectiveAvailabilityChanged(cachedAvailability, mergedAvailability, descriptorsByProvider)
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
    descriptorsByProvider: Map<AgentSessionProvider, AgentSessionProviderDescriptor> = emptyMap(),
  ): Boolean {
    return (previous.keys + updated.keys).any { provider ->
      val defaultAvailability = descriptorsByProvider[provider].defaultAvailabilityBeforeRefresh()
      (previous[provider]?.available ?: defaultAvailability) != (updated[provider]?.available ?: defaultAvailability)
    }
  }

  private fun publishAvailabilityChanged() {
    ActivityTracker.getInstance().inc()
    project.messageBus.syncPublisher(AgentSessionProviderAvailabilityListener.TOPIC).availabilityChanged()
  }
}

private data class ProviderAvailabilityCacheEntry(
  @JvmField val available: Boolean,
  @JvmField val updatedAtMs: Long,
) {
  fun isFresh(nowMs: Long, provider: AgentSessionProviderDescriptor): Boolean {
    return provider.cliVisibilityPolicy == AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE ||
           nowMs - updatedAtMs <= AGENT_SESSION_PROVIDER_AVAILABILITY_CACHE_TTL_MS
  }
}

internal object AgentSessionProviderCliAvailabilityCache {
  private val lock = Any()

  @Volatile
  private var cachedAvailability: Map<AgentSessionProvider, ProviderAvailabilityCacheEntry> = emptyMap()
  private val inFlightAvailability = LinkedHashMap<AgentSessionProvider, CompletableDeferred<ProviderAvailabilityCacheEntry>>()

  suspend fun resolveAvailability(
    provider: AgentSessionProviderDescriptor,
    force: Boolean,
    resolver: suspend () -> Boolean,
  ): Boolean {
    if (force) {
      return probeAndCache(provider, resolver).available
    }

    val action = synchronized(lock) {
      val cached = cachedAvailability[provider.provider]
      if (cached?.isFresh(System.currentTimeMillis(), provider) == true) {
        ProviderAvailabilityResolveAction.Cached(cached)
      }
      else {
        val inFlight = inFlightAvailability[provider.provider]
        if (inFlight != null) {
          ProviderAvailabilityResolveAction.Await(inFlight)
        }
        else {
          val deferred = CompletableDeferred<ProviderAvailabilityCacheEntry>()
          inFlightAvailability[provider.provider] = deferred
          ProviderAvailabilityResolveAction.Run(deferred)
        }
      }
    }

    return when (action) {
      is ProviderAvailabilityResolveAction.Cached -> action.entry.available
      is ProviderAvailabilityResolveAction.Await -> action.deferred.await().available
      is ProviderAvailabilityResolveAction.Run -> runProbe(provider, action.deferred, resolver).available
    }
  }

  private suspend fun runProbe(
    provider: AgentSessionProviderDescriptor,
    deferred: CompletableDeferred<ProviderAvailabilityCacheEntry>,
    resolver: suspend () -> Boolean,
  ): ProviderAvailabilityCacheEntry {
    return try {
      probeAndCache(provider, resolver).also { entry ->
        deferred.complete(entry)
      }
    }
    catch (t: Throwable) {
      deferred.completeExceptionally(t)
      throw t
    }
    finally {
      synchronized(lock) {
        if (inFlightAvailability[provider.provider] === deferred) {
          inFlightAvailability.remove(provider.provider)
        }
      }
    }
  }

  private suspend fun probeAndCache(
    provider: AgentSessionProviderDescriptor,
    resolver: suspend () -> Boolean,
  ): ProviderAvailabilityCacheEntry {
    val entry = ProviderAvailabilityCacheEntry(
      available = resolver(),
      updatedAtMs = System.currentTimeMillis(),
    )
    synchronized(lock) {
      cachedAvailability = cachedAvailability + (provider.provider to entry)
    }
    return entry
  }

  @TestOnly
  fun clearForTest() {
    synchronized(lock) {
      cachedAvailability = emptyMap()
      inFlightAvailability.clear()
    }
  }
}

private sealed interface ProviderAvailabilityResolveAction {
  data class Cached(@JvmField val entry: ProviderAvailabilityCacheEntry) : ProviderAvailabilityResolveAction

  data class Await(@JvmField val deferred: CompletableDeferred<ProviderAvailabilityCacheEntry>) : ProviderAvailabilityResolveAction

  data class Run(@JvmField val deferred: CompletableDeferred<ProviderAvailabilityCacheEntry>) : ProviderAvailabilityResolveAction
}

private fun AgentSessionProviderDescriptor.availabilityFrom(
  snapshot: Map<AgentSessionProvider, ProviderAvailabilityCacheEntry>,
): Boolean {
  return snapshot[provider]?.available ?: defaultAvailabilityBeforeRefresh()
}

private fun AgentSessionProviderDescriptor?.defaultAvailabilityBeforeRefresh(): Boolean {
  return this?.cliVisibilityPolicy != AgentSessionProviderCliVisibilityPolicy.DISCOVER_WHEN_AVAILABLE
}
