// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<AgentSessionRefreshScheduler>()
private const val SOURCE_UPDATE_DEBOUNCE_MS = 350L
private const val SOURCE_REFRESH_GATE_RETRY_MS = 500L

internal class AgentSessionRefreshScheduler(
  private val serviceScope: CoroutineScope,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val scopedRefreshProvidersProvider: () -> List<AgentSessionProvider>,
  private val scopedRefreshSignalsProvider: (AgentSessionProvider) -> Flow<Set<String>>,
  private val isRefreshGateActive: suspend () -> Boolean,
  private val executeFullRefresh: suspend (RefreshLoadScope) -> Unit,
  private val executeProviderRefresh: suspend (
    AgentSessionProvider,
    Long,
    Set<String>?,
    AgentSessionSourceUpdate,
  ) -> Unit,
  private val onFullRefreshFailure: (Throwable) -> Unit,
) {
  private val refreshQueueLock = Any()
  private var pendingRefreshRequest: RefreshRequestType? = null
  private var refreshProcessorRunning = false
  private val sourceRefreshJobs = LinkedHashMap<AgentSessionProvider, PendingSourceRefreshJob>()
  private val sourceRefreshJobsLock = Any()
  private val pendingSourceRefreshProviders = LinkedHashMap<AgentSessionProvider, QueuedSourceRefreshRequest>()
  private var sourceRefreshProcessorRunning = false
  private val sourceRefreshIdCounter = AtomicLong()
  private val sourceObserverJobs = LinkedHashMap<AgentSessionProvider, Job>()
  private val sourceObserverJobsLock = Any()
  private val scopedRefreshObserverJobs = LinkedHashMap<AgentSessionProvider, Job>()
  private val scopedRefreshObserverJobsLock = Any()

  fun observeSessionSourceUpdates() {
    ensureSourceUpdateObservers()
    ensureScopedRefreshObservers()
  }

  fun refresh() {
    enqueueRefresh(RefreshRequestType.FULL_REFRESH)
  }

  fun refreshCatalogAndLoadNewlyOpened() {
    enqueueRefresh(RefreshRequestType.CATALOG_SYNC)
  }

  fun refreshProviderScope(provider: AgentSessionProvider, scopedPaths: Set<String>) {
    enqueueSourceRefresh(provider = provider, scopedPaths = scopedPaths)
  }

  private fun ensureScopedRefreshObservers() {
    val providers = scopedRefreshProvidersProvider()
    synchronized(scopedRefreshObserverJobsLock) {
      providers.forEach { provider ->
        val existing = scopedRefreshObserverJobs[provider]
        if (existing != null && existing.isActive) {
          return@forEach
        }
        scopedRefreshObserverJobs[provider] = serviceScope.launch(Dispatchers.IO) {
          try {
            scopedRefreshSignalsProvider(provider).collect { scopedPaths ->
              if (scopedPaths.isEmpty()) {
                return@collect
              }
              LOG.debug {
                "Received scoped refresh signal for ${provider.value} (paths=${scopedPaths.size}); scheduling scoped provider refresh"
              }
              enqueueSourceRefresh(
                provider = provider,
                scopedPaths = scopedPaths,
                sourceUpdate = AgentSessionSourceUpdate.THREADS_CHANGED,
              )
            }
          }
          catch (e: Throwable) {
            if (e is CancellationException) throw e
            LOG.warn("Scoped refresh observer failed for ${provider.value}", e)
          }
        }
      }
    }
  }

  private fun enqueueRefresh(requestType: RefreshRequestType) {
    var shouldStartProcessor = false
    synchronized(refreshQueueLock) {
      pendingRefreshRequest = mergeRefreshRequestTypes(pendingRefreshRequest, requestType)
      if (!refreshProcessorRunning) {
        refreshProcessorRunning = true
        shouldStartProcessor = true
      }
    }
    if (!shouldStartProcessor) {
      return
    }
    serviceScope.launch(Dispatchers.IO) {
      processQueuedRefreshRequests()
    }
  }

  private suspend fun processQueuedRefreshRequests() {
    while (true) {
      val requestType = synchronized(refreshQueueLock) {
        val next = pendingRefreshRequest
        if (next == null) {
          refreshProcessorRunning = false
          null
        }
        else {
          pendingRefreshRequest = null
          next
        }
      } ?: return

      try {
        ensureSourceUpdateObservers()
        executeRefreshRequest(requestType)
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        LOG.error("Failed to load agent sessions", e)
        onFullRefreshFailure(e)
      }
    }
  }

  private suspend fun executeRefreshRequest(requestType: RefreshRequestType) {
    val loadScope = when (requestType) {
      RefreshRequestType.FULL_REFRESH -> RefreshLoadScope.ALL_OPEN_PROJECTS
      RefreshRequestType.CATALOG_SYNC -> RefreshLoadScope.NEWLY_OPENED_ONLY
    }
    executeFullRefresh(loadScope)
  }

  private fun mergeRefreshRequestTypes(
    current: RefreshRequestType?,
    incoming: RefreshRequestType,
  ): RefreshRequestType {
    if (current == null) {
      return incoming
    }
    return if (current == RefreshRequestType.FULL_REFRESH || incoming == RefreshRequestType.FULL_REFRESH) {
      RefreshRequestType.FULL_REFRESH
    }
    else {
      RefreshRequestType.CATALOG_SYNC
    }
  }

  private fun ensureSourceUpdateObservers() {
    val availableSources = LinkedHashMap<AgentSessionProvider, AgentSessionSource>()
    for (source in sessionSourcesProvider()) {
      if (availableSources.putIfAbsent(source.provider, source) != null) {
        LOG.warn("Duplicate session source for provider ${source.provider.value}; ignoring ${source::class.java.name}")
      }
    }

    synchronized(sourceObserverJobsLock) {
      val jobIterator = sourceObserverJobs.entries.iterator()
      while (jobIterator.hasNext()) {
        val (provider, job) = jobIterator.next()
        val source = availableSources[provider]
        if (source != null && source.supportsUpdates) continue
        LOG.debug { "Stopping source updates observer for ${provider.value}" }
        job.cancel()
        jobIterator.remove()
      }

      for ((provider, source) in availableSources) {
        if (!source.supportsUpdates) continue
        if (sourceObserverJobs.containsKey(provider)) continue

        LOG.debug { "Starting source updates observer for ${provider.value}" }
        val job = serviceScope.launch(Dispatchers.IO) {
          try {
            source.updateEvents.collect { sourceUpdate ->
              scheduleSourceRefresh(provider, sourceUpdate)
            }
          }
          catch (e: Throwable) {
            if (e is CancellationException) throw e
            LOG.warn("Source updates observer failed for ${provider.value}", e)
          }
        }
        sourceObserverJobs[provider] = job
        job.invokeOnCompletion {
          synchronized(sourceObserverJobsLock) {
            if (sourceObserverJobs[provider] === job) {
              sourceObserverJobs.remove(provider)
            }
          }
        }
      }
    }
  }

  private fun scheduleSourceRefresh(provider: AgentSessionProvider, sourceUpdate: AgentSessionSourceUpdate) {
    synchronized(sourceRefreshJobsLock) {
      val existingJob = sourceRefreshJobs.remove(provider)
      existingJob?.job?.cancel()
      val mergedUpdate = mergeSourceUpdateTypes(existingJob?.sourceUpdate, sourceUpdate)
      LOG.debug {
        "Scheduled debounced source refresh for ${provider.value} (sourceUpdate=${mergedUpdate.name.lowercase()})"
      }
      val job = serviceScope.launch(Dispatchers.IO) {
        delay(SOURCE_UPDATE_DEBOUNCE_MS.milliseconds)
        enqueueSourceRefresh(provider = provider, sourceUpdate = mergedUpdate)
      }
      sourceRefreshJobs[provider] = PendingSourceRefreshJob(job = job, sourceUpdate = mergedUpdate)
      job.invokeOnCompletion {
        synchronized(sourceRefreshJobsLock) {
          if (sourceRefreshJobs[provider]?.job === job) {
            sourceRefreshJobs.remove(provider)
          }
        }
      }
    }
  }

  private fun enqueueSourceRefresh(
    provider: AgentSessionProvider,
    scopedPaths: Set<String>? = null,
    sourceUpdate: AgentSessionSourceUpdate = AgentSessionSourceUpdate.THREADS_CHANGED,
  ) {
    val normalizedScopedPaths = scopedPaths
      ?.asSequence()
      ?.map(::normalizeAgentWorkbenchPath)
      ?.filter { it.isNotBlank() }
      ?.toCollection(LinkedHashSet())
      ?.takeIf { it.isNotEmpty() }

    var shouldStartProcessor = false
    var queueSize = 0
    synchronized(sourceRefreshJobsLock) {
      enqueueSourceRefreshLocked(
        provider = provider,
        scopedPaths = normalizedScopedPaths,
        sourceUpdate = sourceUpdate,
      )
      if (!sourceRefreshProcessorRunning) {
        sourceRefreshProcessorRunning = true
        shouldStartProcessor = true
      }
      queueSize = pendingSourceRefreshProviders.size
    }

    LOG.debug {
      val scopeDescription = normalizedScopedPaths?.let { "scopedPaths=${it.size}" } ?: "scopedPaths=all"
      "Enqueued source refresh for ${provider.value} " +
      "($scopeDescription, sourceUpdate=${sourceUpdate.name.lowercase()}, queueSize=$queueSize, startProcessor=$shouldStartProcessor)"
    }

    if (shouldStartProcessor) {
      serviceScope.launch(Dispatchers.IO) {
        processQueuedSourceRefreshes()
      }
    }
  }

  private fun enqueueSourceRefreshLocked(
    provider: AgentSessionProvider,
    scopedPaths: Set<String>?,
    sourceUpdate: AgentSessionSourceUpdate,
  ) {
    val existingRequest = pendingSourceRefreshProviders[provider]
    if (existingRequest == null) {
      pendingSourceRefreshProviders[provider] = QueuedSourceRefreshRequest(
        scopedPaths = scopedPaths,
        sourceUpdate = sourceUpdate,
      )
      return
    }
    pendingSourceRefreshProviders[provider] = QueuedSourceRefreshRequest(
      scopedPaths = mergeSourceRefreshScopes(existingRequest.scopedPaths, scopedPaths),
      sourceUpdate = mergeSourceUpdateTypes(existingRequest.sourceUpdate, sourceUpdate),
    )
  }

  private fun mergeSourceRefreshScopes(existing: Set<String>?, incoming: Set<String>?): Set<String>? {
    if (existing == null || incoming == null) {
      return null
    }
    if (incoming.isEmpty()) {
      return existing
    }
    if (existing.isEmpty()) {
      return incoming
    }
    val merged = LinkedHashSet<String>(existing.size + incoming.size)
    merged.addAll(existing)
    merged.addAll(incoming)
    return merged
  }

  private fun mergeSourceUpdateTypes(
    existing: AgentSessionSourceUpdate?,
    incoming: AgentSessionSourceUpdate,
  ): AgentSessionSourceUpdate {
    if (existing == null) {
      return incoming
    }
    return when {
      existing == AgentSessionSourceUpdate.THREADS_CHANGED || incoming == AgentSessionSourceUpdate.THREADS_CHANGED -> AgentSessionSourceUpdate.THREADS_CHANGED
      else -> AgentSessionSourceUpdate.HINTS_CHANGED
    }
  }

  private suspend fun processQueuedSourceRefreshes() {
    while (true) {
      val dequeued = synchronized(sourceRefreshJobsLock) {
        val nextEntry = pendingSourceRefreshProviders.entries.firstOrNull()
        if (nextEntry == null) {
          sourceRefreshProcessorRunning = false
          null
        }
        else {
          pendingSourceRefreshProviders.remove(nextEntry.key)
          Triple(nextEntry.key, nextEntry.value, pendingSourceRefreshProviders.size)
        }
      } ?: run {
        LOG.debug { "Source refresh processor stopped (queue empty)" }
        return
      }

      val provider = dequeued.first
      val request = dequeued.second
      val scopedPaths = request.scopedPaths
      val sourceUpdate = request.sourceUpdate
      val remainingQueueSize = dequeued.third
      val refreshId = sourceRefreshIdCounter.incrementAndGet()
      LOG.debug {
        val scopeDescription = scopedPaths?.let { "scopedPaths=${it.size}" } ?: "scopedPaths=all"
        "Dequeued source refresh id=$refreshId provider=${provider.value} " +
        "($scopeDescription, sourceUpdate=${sourceUpdate.name.lowercase()}, remainingQueueSize=$remainingQueueSize)"
      }

      val gateActive = try {
        isRefreshGateActive()
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        LOG.warn("Failed to evaluate source refresh gate", e)
        false
      }

      LOG.debug {
        "Source refresh gate evaluated for id=$refreshId provider=${provider.value}: active=$gateActive"
      }

      if (!gateActive) {
        var queueSizeAfterRequeue = 0
        synchronized(sourceRefreshJobsLock) {
          enqueueSourceRefreshLocked(
            provider = provider,
            scopedPaths = scopedPaths,
            sourceUpdate = sourceUpdate,
          )
          queueSizeAfterRequeue = pendingSourceRefreshProviders.size
        }
        LOG.debug {
          "Source refresh gate blocked id=$refreshId provider=${provider.value}; requeued (queueSize=$queueSizeAfterRequeue)"
        }
        delay(SOURCE_REFRESH_GATE_RETRY_MS.milliseconds)
        continue
      }

      try {
        executeProviderRefresh(
          provider,
          refreshId,
          scopedPaths,
          sourceUpdate,
        )
      }
      catch (e: Throwable) {
        if (e is CancellationException) throw e
        LOG.warn("Failed to refresh ${provider.value} sessions from queued update", e)
      }
    }
  }

  private enum class RefreshRequestType {
    CATALOG_SYNC,
    FULL_REFRESH,
  }
}

private data class PendingSourceRefreshJob(
  @JvmField val job: Job,
  @JvmField val sourceUpdate: AgentSessionSourceUpdate,
)

private data class QueuedSourceRefreshRequest(
  @JvmField val scopedPaths: Set<String>?,
  @JvmField val sourceUpdate: AgentSessionSourceUpdate,
)
