// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSourceUpdateEvent
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadActivityUpdate
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionThreadPresentationUpdate
import com.intellij.agent.workbench.sessions.core.providers.describeScope
import com.intellij.agent.workbench.sessions.core.providers.isUnscoped
import com.intellij.agent.workbench.sessions.core.providers.mergeAgentSessionThreadPresentationUpdates
import com.intellij.agent.workbench.sessions.core.providers.toPresentationUpdate
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
private const val SOURCE_OBSERVER_RESTART_DELAY_MS = 1_000L

internal class AgentSessionRefreshScheduler(
  private val serviceScope: CoroutineScope,
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val scopedRefreshProvidersProvider: () -> List<AgentSessionProvider>,
  private val scopedRefreshSignalsProvider: (AgentSessionProvider) -> Flow<AgentSessionSourceUpdateEvent>,
  private val isRefreshGateActive: suspend () -> Boolean,
  private val executeFullRefresh: suspend (RefreshLoadScope) -> Unit,
  private val executeProviderRefresh: suspend (AgentSessionProvider, Long, AgentSessionSourceUpdateEvent) -> Unit,
  private val executeProviderHintRefresh: suspend (AgentSessionProvider, Long, AgentSessionSourceUpdateEvent) -> Unit,
  private val applySourceUpdatePresentationHints: (AgentSessionProvider, AgentSessionSourceUpdateEvent) -> Unit = { _, _ -> },
  private val scheduleVfsRefreshForSourceUpdate: (AgentSessionProvider, AgentSessionSourceUpdateEvent) -> Unit = { _, _ -> },
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
    enqueueSourceRefresh(
      provider = provider,
      updateEvent = AgentSessionSourceUpdateEvent(
        type = AgentSessionSourceUpdate.THREADS_CHANGED,
        scopedPaths = scopedPaths,
      ),
    )
  }

  private fun ensureScopedRefreshObservers() {
    val providers = scopedRefreshProvidersProvider()
    synchronized(scopedRefreshObserverJobsLock) {
      providers.forEach { provider ->
        val existing = scopedRefreshObserverJobs[provider]
        if (existing != null && existing.isActive) {
          return@forEach
        }
        val job = serviceScope.launch(Dispatchers.IO) {
          try {
            scopedRefreshSignalsProvider(provider).collect { updateEvent ->
              val normalizedUpdateEvent = normalizeUpdateEvent(updateEvent)
              if (normalizedUpdateEvent.isUnscoped()) {
                return@collect
              }
              LOG.debug {
                "Received scoped refresh signal for ${provider.value} (${normalizedUpdateEvent.describeScope()}); " +
                "processing scoped source update"
              }
              scheduleSourceRefresh(
                provider = provider,
                updateEvent = normalizedUpdateEvent,
              )
            }
          }
          catch (e: Throwable) {
            if (e is CancellationException) throw e
            LOG.warn("Scoped refresh observer failed for ${provider.value}", e)
            scheduleScopedRefreshObserverRestart(provider)
          }
        }
        scopedRefreshObserverJobs[provider] = job
        job.invokeOnCompletion {
          synchronized(scopedRefreshObserverJobsLock) {
            if (scopedRefreshObserverJobs[provider] === job) {
              scopedRefreshObserverJobs.remove(provider)
            }
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
            source.updateEvents.collect { updateEvent ->
              scheduleSourceRefresh(provider, updateEvent)
            }
          }
          catch (e: Throwable) {
            if (e is CancellationException) throw e
            LOG.warn("Source updates observer failed for ${provider.value}", e)
            scheduleSourceUpdateObserverRestart(provider)
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

  private fun scheduleSourceUpdateObserverRestart(provider: AgentSessionProvider) {
    serviceScope.launch(Dispatchers.IO) {
      delay(SOURCE_OBSERVER_RESTART_DELAY_MS.milliseconds)
      LOG.debug { "Restarting source updates observer for ${provider.value} after failure" }
      ensureSourceUpdateObservers()
    }
  }

  private fun scheduleScopedRefreshObserverRestart(provider: AgentSessionProvider) {
    serviceScope.launch(Dispatchers.IO) {
      delay(SOURCE_OBSERVER_RESTART_DELAY_MS.milliseconds)
      LOG.debug { "Restarting scoped refresh observer for ${provider.value} after failure" }
      ensureScopedRefreshObservers()
    }
  }

  private fun scheduleSourceRefresh(provider: AgentSessionProvider, updateEvent: AgentSessionSourceUpdateEvent) {
    val normalizedIncoming = normalizeUpdateEvent(updateEvent)
    applySourceUpdatePresentationHints(provider, normalizedIncoming)
    if (!requiresQueuedProviderRefresh(normalizedIncoming)) {
      LOG.debug {
        "Applied source update for ${provider.value} without provider refresh " +
        "(${normalizedIncoming.describeScope()}, sourceUpdate=${normalizedIncoming.type.name.lowercase()})"
      }
      return
    }
    synchronized(sourceRefreshJobsLock) {
      val existingJob = sourceRefreshJobs.remove(provider)
      existingJob?.job?.cancel()
      val mergedUpdate = mergeSourceUpdateEvents(existingJob?.updateEvent, normalizedIncoming)
      LOG.debug {
        "Scheduled debounced source refresh for ${provider.value} " +
        "(${mergedUpdate.describeScope()}, sourceUpdate=${mergedUpdate.type.name.lowercase()})"
      }
      val job = serviceScope.launch(Dispatchers.IO) {
        delay(SOURCE_UPDATE_DEBOUNCE_MS.milliseconds)
        if (mergedUpdate.mayHaveChangedProjectFiles) {
          scheduleVfsRefreshFromSourceUpdate(provider, mergedUpdate)
        }
        enqueueSourceRefresh(provider = provider, updateEvent = mergedUpdate, applyPresentationHints = false)
      }
      sourceRefreshJobs[provider] = PendingSourceRefreshJob(job = job, updateEvent = mergedUpdate)
      job.invokeOnCompletion {
        synchronized(sourceRefreshJobsLock) {
          if (sourceRefreshJobs[provider]?.job === job) {
            sourceRefreshJobs.remove(provider)
          }
        }
      }
    }
  }

  private fun scheduleVfsRefreshFromSourceUpdate(provider: AgentSessionProvider, updateEvent: AgentSessionSourceUpdateEvent) {
    try {
      scheduleVfsRefreshForSourceUpdate(provider, updateEvent)
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to schedule VFS refresh for ${provider.value} source update", e)
    }
  }

  private fun enqueueSourceRefresh(
    provider: AgentSessionProvider,
    updateEvent: AgentSessionSourceUpdateEvent,
    applyPresentationHints: Boolean = true,
  ) {
    val normalizedUpdateEvent = normalizeUpdateEvent(updateEvent)
    if (applyPresentationHints) {
      applySourceUpdatePresentationHints(provider, normalizedUpdateEvent)
    }

    var shouldStartProcessor = false
    var queueSize = 0
    synchronized(sourceRefreshJobsLock) {
      enqueueSourceRefreshLocked(
        provider = provider,
        updateEvent = normalizedUpdateEvent,
      )
      if (!sourceRefreshProcessorRunning) {
        sourceRefreshProcessorRunning = true
        shouldStartProcessor = true
      }
      queueSize = pendingSourceRefreshProviders.size
    }

    LOG.debug {
      "Enqueued source refresh for ${provider.value} " +
      "(${normalizedUpdateEvent.describeScope()}, sourceUpdate=${normalizedUpdateEvent.type.name.lowercase()}, " +
      "queueSize=$queueSize, startProcessor=$shouldStartProcessor)"
    }

    if (shouldStartProcessor) {
      serviceScope.launch(Dispatchers.IO) {
        processQueuedSourceRefreshes()
      }
    }
  }

  private fun enqueueSourceRefreshLocked(
    provider: AgentSessionProvider,
    updateEvent: AgentSessionSourceUpdateEvent,
  ) {
    val existingRequest = pendingSourceRefreshProviders[provider]
    if (existingRequest == null) {
      pendingSourceRefreshProviders[provider] = QueuedSourceRefreshRequest(
        updateEvent = updateEvent,
      )
      return
    }
    pendingSourceRefreshProviders[provider] = QueuedSourceRefreshRequest(
      updateEvent = mergeSourceUpdateEvents(existingRequest.updateEvent, updateEvent),
    )
  }

  private fun normalizeUpdateEvent(updateEvent: AgentSessionSourceUpdateEvent): AgentSessionSourceUpdateEvent {
    val activityUpdatesByThreadId = normalizeActivityUpdates(updateEvent.activityUpdatesByThreadId)
    return AgentSessionSourceUpdateEvent(
      type = updateEvent.type,
      scopedPaths = normalizePaths(updateEvent.scopedPaths),
      threadIds = normalizeThreadIds(updateEvent.threadIds),
      activityUpdatesByThreadId = activityUpdatesByThreadId,
      presentationUpdatesByThreadId = mergePresentationUpdates(
        activityUpdatesByThreadId.mapValues { (_, update) -> update.toPresentationUpdate() },
        normalizePresentationUpdates(updateEvent.presentationUpdatesByThreadId),
      ),
      mayHaveChangedProjectFiles = updateEvent.mayHaveChangedProjectFiles,
      changedProjectFilePaths = normalizePaths(updateEvent.changedProjectFilePaths),
    )
  }

  private fun normalizePaths(paths: Set<String>?): Set<String>? {
    return paths
      ?.asSequence()
      ?.map(::normalizeAgentWorkbenchPath)
      ?.filter { it.isNotBlank() }
      ?.toCollection(LinkedHashSet())
      ?.takeIf { it.isNotEmpty() }
  }

  private fun normalizeThreadIds(threadIds: Set<String>?): Set<String>? {
    return threadIds
      ?.asSequence()
      ?.map(String::trim)
      ?.filter { it.isNotBlank() }
      ?.toCollection(LinkedHashSet())
      ?.takeIf { it.isNotEmpty() }
  }

  private fun normalizeActivityUpdates(
    activityUpdatesByThreadId: Map<String, AgentSessionThreadActivityUpdate>,
  ): Map<String, AgentSessionThreadActivityUpdate> {
    if (activityUpdatesByThreadId.isEmpty()) {
      return emptyMap()
    }
    val normalized = LinkedHashMap<String, AgentSessionThreadActivityUpdate>(activityUpdatesByThreadId.size)
    for ((threadId, update) in activityUpdatesByThreadId) {
      val normalizedThreadId = threadId.trim()
      if (normalizedThreadId.isNotEmpty()) {
        normalized[normalizedThreadId] = update
      }
    }
    return normalized
  }

  private fun normalizePresentationUpdates(
    presentationUpdatesByThreadId: Map<String, AgentSessionThreadPresentationUpdate>,
  ): Map<String, AgentSessionThreadPresentationUpdate> {
    if (presentationUpdatesByThreadId.isEmpty()) {
      return emptyMap()
    }
    val normalized = LinkedHashMap<String, AgentSessionThreadPresentationUpdate>(presentationUpdatesByThreadId.size)
    for ((threadId, update) in presentationUpdatesByThreadId) {
      val normalizedThreadId = threadId.trim()
      if (normalizedThreadId.isNotEmpty()) {
        normalized[normalizedThreadId] = update
      }
    }
    return normalized
  }

  private fun mergeSourceUpdateEvents(
    existing: AgentSessionSourceUpdateEvent?,
    incoming: AgentSessionSourceUpdateEvent,
  ): AgentSessionSourceUpdateEvent {
    if (existing == null) {
      return incoming
    }

    val mergedType = when {
      existing.type == AgentSessionSourceUpdate.THREADS_CHANGED ||
      incoming.type == AgentSessionSourceUpdate.THREADS_CHANGED -> AgentSessionSourceUpdate.THREADS_CHANGED
      else -> AgentSessionSourceUpdate.HINTS_CHANGED
    }
    val mergedActivityUpdatesByThreadId = mergeActivityUpdates(existing.activityUpdatesByThreadId, incoming.activityUpdatesByThreadId)
    val mergedPresentationUpdatesByThreadId = mergePresentationUpdates(
      existing.presentationUpdatesByThreadId,
      incoming.presentationUpdatesByThreadId,
    )
    val mergedChangedProjectFilePaths = mergeChangedProjectFilePaths(existing, incoming)
    if (existing.isUnscoped() || incoming.isUnscoped()) {
      return AgentSessionSourceUpdateEvent(
        type = mergedType,
        activityUpdatesByThreadId = mergedActivityUpdatesByThreadId,
        presentationUpdatesByThreadId = mergedPresentationUpdatesByThreadId,
        mayHaveChangedProjectFiles = existing.mayHaveChangedProjectFiles || incoming.mayHaveChangedProjectFiles,
        changedProjectFilePaths = mergedChangedProjectFilePaths,
      )
    }

    return AgentSessionSourceUpdateEvent(
      type = mergedType,
      scopedPaths = mergeScopeSets(existing.scopedPaths, incoming.scopedPaths),
      threadIds = mergeScopeSets(existing.threadIds, incoming.threadIds),
      activityUpdatesByThreadId = mergedActivityUpdatesByThreadId,
      presentationUpdatesByThreadId = mergedPresentationUpdatesByThreadId,
      mayHaveChangedProjectFiles = existing.mayHaveChangedProjectFiles || incoming.mayHaveChangedProjectFiles,
      changedProjectFilePaths = mergedChangedProjectFilePaths,
    )
  }

  private fun mergeChangedProjectFilePaths(
    existing: AgentSessionSourceUpdateEvent,
    incoming: AgentSessionSourceUpdateEvent,
  ): Set<String>? {
    if (!existing.mayHaveChangedProjectFiles) {
      return incoming.changedProjectFilePaths
    }
    if (!incoming.mayHaveChangedProjectFiles) {
      return existing.changedProjectFilePaths
    }
    if (existing.changedProjectFilePaths == null || incoming.changedProjectFilePaths == null) {
      return null
    }
    return mergeScopeSets(existing.changedProjectFilePaths, incoming.changedProjectFilePaths)
  }

  private fun mergeActivityUpdates(
    existing: Map<String, AgentSessionThreadActivityUpdate>,
    incoming: Map<String, AgentSessionThreadActivityUpdate>,
  ): Map<String, AgentSessionThreadActivityUpdate> {
    if (existing.isEmpty()) return incoming
    if (incoming.isEmpty()) return existing
    val merged = LinkedHashMap<String, AgentSessionThreadActivityUpdate>(existing.size + incoming.size)
    merged.putAll(existing)
    for ((threadId, incomingUpdate) in incoming) {
      val existingUpdate = merged[threadId]
      merged[threadId] = if (existingUpdate == null) incomingUpdate else mergeActivityUpdate(existingUpdate, incomingUpdate)
    }
    return merged
  }

  private fun mergeActivityUpdate(
    existing: AgentSessionThreadActivityUpdate,
    incoming: AgentSessionThreadActivityUpdate,
  ): AgentSessionThreadActivityUpdate {
    val existingUpdatedAt = existing.updatedAt
    val incomingUpdatedAt = incoming.updatedAt
    if (existingUpdatedAt != null && incomingUpdatedAt != null && incomingUpdatedAt < existingUpdatedAt) {
      return existing
    }
    val updatedAt = when {
      existingUpdatedAt == null -> incomingUpdatedAt
      incomingUpdatedAt == null -> existingUpdatedAt
      else -> maxOf(existingUpdatedAt, incomingUpdatedAt)
    }
    val updatesChromeActivity = incoming.updatesChromeActivity || existing.updatesChromeActivity
    return AgentSessionThreadActivityUpdate(
      activityReport = incoming.activityReport.copy(
        chromeActivity = if (incoming.updatesChromeActivity) incoming.activityReport.chromeActivity else existing.activityReport.chromeActivity,
      ),
      updatesChromeActivity = updatesChromeActivity,
      updatedAt = updatedAt,
    )
  }

  private fun mergePresentationUpdates(
    existing: Map<String, AgentSessionThreadPresentationUpdate>,
    incoming: Map<String, AgentSessionThreadPresentationUpdate>,
  ): Map<String, AgentSessionThreadPresentationUpdate> {
    if (existing.isEmpty()) return incoming
    if (incoming.isEmpty()) return existing
    val merged = LinkedHashMap<String, AgentSessionThreadPresentationUpdate>(existing.size + incoming.size)
    merged.putAll(existing)
    for ((threadId, incomingUpdate) in incoming) {
      val existingUpdate = merged[threadId]
      merged[threadId] = if (existingUpdate == null) incomingUpdate else mergeAgentSessionThreadPresentationUpdates(existingUpdate, incomingUpdate)
    }
    return merged
  }

  private fun <T> mergeScopeSets(existing: Set<T>?, incoming: Set<T>?): Set<T>? {
    if (existing == null) {
      return incoming
    }
    if (incoming == null) {
      return existing
    }
    val merged = LinkedHashSet<T>(existing.size + incoming.size)
    merged.addAll(existing)
    merged.addAll(incoming)
    return merged
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
      val updateEvent = request.updateEvent
      val remainingQueueSize = dequeued.third
      val refreshId = sourceRefreshIdCounter.incrementAndGet()
      LOG.debug {
        "Dequeued source refresh id=$refreshId provider=${provider.value} " +
        "(${updateEvent.describeScope()}, sourceUpdate=${updateEvent.type.name.lowercase()}, remainingQueueSize=$remainingQueueSize)"
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
            updateEvent = updateEvent,
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
        when (updateEvent.type) {
          AgentSessionSourceUpdate.THREADS_CHANGED -> executeProviderRefresh(
            provider,
            refreshId,
            updateEvent,
          )
          AgentSessionSourceUpdate.HINTS_CHANGED -> executeProviderHintRefresh(
            provider,
            refreshId,
            updateEvent,
          )
        }
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

private fun requiresQueuedProviderRefresh(updateEvent: AgentSessionSourceUpdateEvent): Boolean {
  if (updateEvent.mayHaveChangedProjectFiles) {
    return true
  }
  if (updateEvent.type == AgentSessionSourceUpdate.THREADS_CHANGED) {
    return true
  }
  if (updateEvent.isUnscoped()) {
    return false
  }
  if (!updateEvent.threadIds.isNullOrEmpty()) {
    return true
  }
  return updateEvent.presentationUpdatesByThreadId.isEmpty()
}

private data class PendingSourceRefreshJob(
  @JvmField val job: Job,
  @JvmField val updateEvent: AgentSessionSourceUpdateEvent,
)

private data class QueuedSourceRefreshRequest(
  @JvmField val updateEvent: AgentSessionSourceUpdateEvent,
)
