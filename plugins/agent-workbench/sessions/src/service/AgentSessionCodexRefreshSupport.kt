// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatConcreteCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.codex.CodexPendingTabMatcher
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.buildAgentSessionResumeLaunchSpec
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.agent.workbench.sessions.util.parseAgentSessionIdentity
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = logger<AgentSessionCodexRefreshSupport>()
private const val PENDING_CODEX_MATCH_PRE_WINDOW_MS = 20_000L
private const val PENDING_CODEX_MATCH_POST_WINDOW_MS = 120_000L
private const val PENDING_CODEX_NO_BASELINE_AUTO_BIND_MAX_AGE_MS = PENDING_CODEX_MATCH_POST_WINDOW_MS
private const val PENDING_CODEX_AMBIGUITY_NOTIFY_AFTER_POLLS = 2
private const val PENDING_CODEX_AMBIGUITY_NOTIFY_COOLDOWN_MS = 5 * 60 * 1000L
private const val CONCRETE_CODEX_NEW_THREAD_MATCH_PRE_WINDOW_MS = 5_000L
private const val CONCRETE_CODEX_NEW_THREAD_MATCH_POST_WINDOW_MS = 30_000L
private const val CONCRETE_CODEX_NEW_THREAD_REBIND_MAX_AGE_MS = CONCRETE_CODEX_NEW_THREAD_MATCH_POST_WINDOW_MS
private const val CODEX_REFRESH_HINT_MAX_LISTED_THREAD_IDS_PER_PATH = 200

internal data class PendingCodexBindOutcome(
  val pendingTabsForProjectionByPath: Map<String, List<AgentChatPendingCodexTabSnapshot>>,
)

internal class AgentSessionCodexRefreshSupport(
  private val provider: AgentSessionProvider,
  private val openPendingCodexTabsProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatPendingCodexTabSnapshot>>,
  private val openConcreteCodexTabsAwaitingNewThreadRebindProvider: suspend (AgentSessionProvider) -> Map<String, List<AgentChatConcreteCodexTabSnapshot>>,
  private val openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>>,
  private val openAgentChatPendingTabsBinder: (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
  ) -> AgentChatPendingCodexTabRebindReport,
  private val openAgentChatConcreteTabsBinder: (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteCodexTabRebindRequest>>,
  ) -> AgentChatConcreteCodexTabRebindReport,
  private val clearOpenConcreteCodexTabAnchors: (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteCodexTabSnapshot>>,
  ) -> Int,
) {
  private val pendingCodexAmbiguityLock = Any()
  private val pendingCodexAmbiguityStateByKey = LinkedHashMap<String, PendingCodexAmbiguityState>()

  suspend fun collectNormalizedPendingTabsByPath(): Map<String, List<AgentChatPendingCodexTabSnapshot>> {
    val pendingTabsByPath = try {
      openPendingCodexTabsProvider(provider)
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to collect pending Codex tabs for provider refresh", e)
      return emptyMap()
    }
    if (pendingTabsByPath.isEmpty()) {
      return emptyMap()
    }

    val normalized = LinkedHashMap<String, MutableList<AgentChatPendingCodexTabSnapshot>>(pendingTabsByPath.size)
    for ((path, pendingTabs) in pendingTabsByPath) {
      if (pendingTabs.isEmpty()) {
        continue
      }
      val normalizedPath = normalizeAgentWorkbenchPath(path)
      normalized.getOrPut(normalizedPath) { ArrayList(pendingTabs.size) }.addAll(pendingTabs)
    }
    return normalized
  }

  suspend fun collectNormalizedConcreteTabsAwaitingNewThreadRebindByPath(): Map<String, List<AgentChatConcreteCodexTabSnapshot>> {
    val concreteTabsByPath = try {
      openConcreteCodexTabsAwaitingNewThreadRebindProvider(provider)
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to collect concrete Codex tabs awaiting /new rebind", e)
      return emptyMap()
    }
    if (concreteTabsByPath.isEmpty()) {
      return emptyMap()
    }

    val normalized = LinkedHashMap<String, MutableList<AgentChatConcreteCodexTabSnapshot>>(concreteTabsByPath.size)
    for ((path, concreteTabs) in concreteTabsByPath) {
      if (concreteTabs.isEmpty()) {
        continue
      }
      val normalizedPath = normalizeAgentWorkbenchPath(path)
      normalized.getOrPut(normalizedPath) { ArrayList(concreteTabs.size) }.addAll(concreteTabs)
    }
    return normalized
  }

  suspend fun collectRefreshHintThreadIdsByPath(
    targetPaths: Set<String>,
    outcomes: Map<String, ProviderRefreshOutcome>,
    knownThreadIdsByPath: Map<String, Set<String>>,
    pendingTabsByPath: Map<String, List<AgentChatPendingCodexTabSnapshot>>,
    concreteTabsByPath: Map<String, List<AgentChatConcreteCodexTabSnapshot>> = emptyMap(),
  ): Map<String, Set<String>> {
    if (targetPaths.isEmpty()) {
      return emptyMap()
    }

    val hintThreadIdsByPath = LinkedHashMap<String, LinkedHashSet<String>>()
    for (path in targetPaths) {
      val baselineKnownThreadIds = knownThreadIdsByPath[path].orEmpty()
      val pathHasConcreteTabsAwaitingNewThreadRebind = concreteTabsByPath[path]?.isNotEmpty() == true
      val ids = LinkedHashSet<String>()
      baselineKnownThreadIds
        .asSequence()
        .filterNot(::isAgentSessionNewSessionId)
        .forEach(ids::add)

      outcomes[path]
        ?.threads
        .orEmpty()
        .asSequence()
        .filter { thread -> thread.provider == provider }
        .sortedByDescending { thread -> thread.updatedAt }
        .take(CODEX_REFRESH_HINT_MAX_LISTED_THREAD_IDS_PER_PATH)
        .forEach { thread ->
          if (!pathHasConcreteTabsAwaitingNewThreadRebind || thread.id in baselineKnownThreadIds) {
            ids.add(thread.id)
          }
        }

      pendingTabsByPath[path]
        .orEmpty()
        .forEach { pendingTab ->
          val identity = parseAgentSessionIdentity(pendingTab.pendingThreadIdentity) ?: return@forEach
          if (identity.provider != provider || isAgentSessionNewSessionId(identity.sessionId)) {
            return@forEach
          }
          ids.add(identity.sessionId)
        }

      if (ids.isNotEmpty() || pendingTabsByPath[path]?.isNotEmpty() == true) {
        hintThreadIdsByPath[path] = ids
      }
    }

    val openConcreteThreadIdentitiesByPath = try {
      openConcreteChatThreadIdentitiesByPathProvider()
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to collect open concrete chat thread identities for Codex refresh hints", e)
      emptyMap()
    }

    for ((path, threadIdentities) in openConcreteThreadIdentitiesByPath) {
      val normalizedPath = normalizeAgentWorkbenchPath(path)
      if (normalizedPath !in targetPaths) {
        continue
      }

      val ids = hintThreadIdsByPath.getOrPut(normalizedPath) { LinkedHashSet() }
      for (threadIdentity in threadIdentities) {
        val identity = parseAgentSessionIdentity(threadIdentity) ?: continue
        if (identity.provider != provider || isAgentSessionNewSessionId(identity.sessionId)) {
          continue
        }
        ids.add(identity.sessionId)
      }
    }

    return hintThreadIdsByPath.mapValues { (_, ids) -> ids.toCollection(LinkedHashSet()) }
  }

  fun applyActivityHints(
    outcomes: MutableMap<String, ProviderRefreshOutcome>,
    refreshHintsByPath: Map<String, AgentSessionRefreshHints>,
  ) {
    if (outcomes.isEmpty() || refreshHintsByPath.isEmpty()) {
      return
    }

    val updatedOutcomes = LinkedHashMap<String, ProviderRefreshOutcome>()
    for ((path, outcome) in outcomes) {
      val threads = outcome.threads ?: continue
      val activityByThreadId = refreshHintsByPath[path]?.activityByThreadId ?: continue
      if (activityByThreadId.isEmpty()) {
        continue
      }

      var changed = false
      val updatedThreads = threads.map { thread ->
        if (thread.provider != provider) {
          return@map thread
        }
        val hintedActivity = activityByThreadId[thread.id] ?: return@map thread
        if (hintedActivity == thread.activity) {
          return@map thread
        }
        changed = true
        thread.copy(activity = hintedActivity)
      }

      if (changed) {
        updatedOutcomes[path] = outcome.copy(threads = updatedThreads)
      }
    }

    if (updatedOutcomes.isEmpty()) {
      return
    }

    for ((path, updatedOutcome) in updatedOutcomes) {
      outcomes[path] = updatedOutcome
    }
  }

  suspend fun bindPendingOpenChatTabs(
    outcomes: Map<String, ProviderRefreshOutcome>,
    refreshId: Long,
    allowedThreadIdsByPath: Map<String, Set<String>>? = null,
    refreshHintsByPath: Map<String, AgentSessionRefreshHints> = emptyMap(),
    pendingTabsByPath: Map<String, List<AgentChatPendingCodexTabSnapshot>> = emptyMap(),
  ): PendingCodexBindOutcome {
    if (pendingTabsByPath.isEmpty()) {
      clearPendingCodexAmbiguityState()
      return PendingCodexBindOutcome(emptyMap())
    }
    val eligiblePendingTabsByPath = selectPendingTabsEligibleForRebind(
      pendingTabsByPath = pendingTabsByPath,
      allowedThreadIdsByPath = allowedThreadIdsByPath,
      nowMs = System.currentTimeMillis(),
    )
    if (eligiblePendingTabsByPath.isEmpty()) {
      clearPendingCodexAmbiguityState()
      return PendingCodexBindOutcome(pendingTabsForProjectionByPath = pendingTabsByPath)
    }

    val candidatesByPath = LinkedHashMap<String, MutableList<AgentChatTabRebindTarget>>()
    for ((path, outcome) in outcomes) {
      val threads = outcome.threads ?: continue
      val hasEligiblePendingTabs = eligiblePendingTabsByPath[path]?.isNotEmpty() == true
      val allowedThreadIds = allowedThreadIdsByPath?.get(path)
      if (allowedThreadIdsByPath != null && allowedThreadIds == null && !hasEligiblePendingTabs) {
        continue
      }
      for (thread in threads) {
        if (thread.provider != provider) continue
        if (allowedThreadIds != null && thread.id !in allowedThreadIds) continue
        candidatesByPath.getOrPut(path) { ArrayList() }.add(
          buildCodexRebindTarget(
            provider = provider,
            threadId = thread.id,
            title = thread.title,
            activity = thread.activity,
            updatedAt = thread.updatedAt,
          )
        )
      }
    }

    for ((path, refreshHints) in refreshHintsByPath) {
      val rebindCandidates = refreshHints.rebindCandidates
      if (rebindCandidates.isEmpty()) {
        continue
      }
      val hasEligiblePendingTabs = eligiblePendingTabsByPath[path]?.isNotEmpty() == true
      if (allowedThreadIdsByPath != null && !allowedThreadIdsByPath.containsKey(path) && !hasEligiblePendingTabs) {
        continue
      }
      val pathCandidates = candidatesByPath.getOrPut(path) { ArrayList(rebindCandidates.size) }
      for (candidate in rebindCandidates) {
        pathCandidates.add(
          buildCodexRebindTarget(
            provider = provider,
            threadId = candidate.threadId,
            title = candidate.title,
            activity = candidate.activity,
            updatedAt = candidate.updatedAt,
          )
        )
      }
    }

    if (candidatesByPath.isEmpty()) {
      return PendingCodexBindOutcome(pendingTabsForProjectionByPath = pendingTabsByPath)
    }

    val openConcreteThreadIdentitiesByPath = openConcreteChatThreadIdentitiesByPathProvider()
    val matchResult = CodexPendingTabMatcher.match(
      pendingTabsByPath = eligiblePendingTabsByPath,
      candidatesByPath = candidatesByPath,
      openConcreteIdentitiesByPath = openConcreteThreadIdentitiesByPath,
      preWindowMs = PENDING_CODEX_MATCH_PRE_WINDOW_MS,
      postWindowMs = PENDING_CODEX_MATCH_POST_WINDOW_MS,
    )

    reportPendingCodexMatchingGaps(
      refreshId = refreshId,
      ambiguousByPath = matchResult.ambiguousPendingThreadIdentitiesByPath,
      noMatchByPath = matchResult.noMatchPendingThreadIdentitiesByPath,
    )

    val bindingsByPath = matchResult.bindingsByPath
    if (bindingsByPath.isEmpty()) {
      return PendingCodexBindOutcome(pendingTabsForProjectionByPath = pendingTabsByPath)
    }

    val requestsByPath = LinkedHashMap<String, List<AgentChatPendingCodexTabRebindRequest>>(bindingsByPath.size)
    for ((path, bindings) in bindingsByPath) {
      requestsByPath[path] = bindings.map { binding ->
        AgentChatPendingCodexTabRebindRequest(
          pendingTabKey = binding.pendingTabKey,
          pendingThreadIdentity = binding.pendingThreadIdentity,
          target = binding.target,
        )
      }
    }

    val rebindReport = withContext(Dispatchers.UI) {
      openAgentChatPendingTabsBinder(provider, requestsByPath)
    }

    LOG.debug {
      "Provider refresh id=$refreshId provider=${provider.value} rebound pending chat tabs " +
      "(reboundBindings=${rebindReport.reboundBindings}, reboundFiles=${rebindReport.reboundFiles}, " +
      "requestedBindings=${rebindReport.requestedBindings}, candidatePaths=${candidatesByPath.size}, matchedPaths=${bindingsByPath.size})"
    }

    return PendingCodexBindOutcome(
      pendingTabsForProjectionByPath = reconcilePendingTabsForProjection(
        pendingTabsByPath = pendingTabsByPath,
        rebindReport = rebindReport,
      ),
    )
  }

  suspend fun bindConcreteOpenChatTabsAwaitingNewThread(
    refreshId: Long,
    refreshHintsByPath: Map<String, AgentSessionRefreshHints>,
    concreteTabsByPath: Map<String, List<AgentChatConcreteCodexTabSnapshot>> = emptyMap(),
  ) {
    if (concreteTabsByPath.isEmpty()) {
      return
    }

    val nowMs = System.currentTimeMillis()
    val staleTabsByPath = LinkedHashMap<String, List<AgentChatConcreteCodexTabSnapshot>>()
    val eligibleTabsByPath = LinkedHashMap<String, List<AgentChatConcreteCodexTabSnapshot>>()
    for ((path, tabs) in concreteTabsByPath) {
      if (tabs.isEmpty()) {
        continue
      }
      val staleTabs = tabs.filter { tab ->
        nowMs < tab.newThreadRebindRequestedAtMs ||
        nowMs - tab.newThreadRebindRequestedAtMs > CONCRETE_CODEX_NEW_THREAD_REBIND_MAX_AGE_MS
      }
      if (staleTabs.isNotEmpty()) {
        staleTabsByPath[path] = staleTabs
      }
      val eligibleTabs = tabs.filterNot(staleTabs::contains)
      if (eligibleTabs.isNotEmpty()) {
        eligibleTabsByPath[path] = eligibleTabs
      }
    }

    if (staleTabsByPath.isNotEmpty()) {
      val cleared = withContext(Dispatchers.UI) {
        clearOpenConcreteCodexTabAnchors(provider, staleTabsByPath)
      }
      LOG.debug {
        "Provider refresh id=$refreshId provider=codex cleared stale /new tab anchors " +
        "(paths=${staleTabsByPath.size}, cleared=$cleared)"
      }
    }

    if (eligibleTabsByPath.isEmpty() || refreshHintsByPath.isEmpty()) {
      return
    }

    val openConcreteThreadIdentitiesByPath = try {
      openConcreteChatThreadIdentitiesByPathProvider()
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      LOG.warn("Failed to collect open concrete chat thread identities for /new rebind", e)
      emptyMap()
    }
    val unavailableThreadIdentitiesByPath = LinkedHashMap<String, LinkedHashSet<String>>(openConcreteThreadIdentitiesByPath.size)
    for ((path, threadIdentities) in openConcreteThreadIdentitiesByPath) {
      unavailableThreadIdentitiesByPath[normalizeAgentWorkbenchPath(path)] = LinkedHashSet(threadIdentities)
    }

    val requestsByPath = LinkedHashMap<String, List<AgentChatConcreteCodexTabRebindRequest>>()
    for ((path, concreteTabs) in eligibleTabsByPath) {
      val rebindCandidates = refreshHintsByPath[path]?.rebindCandidates.orEmpty()
      if (rebindCandidates.isEmpty()) {
        continue
      }
      val unavailableThreadIdentities = unavailableThreadIdentitiesByPath.getOrPut(path) { LinkedHashSet() }
      val candidates = rebindCandidates.map { candidate ->
        buildCodexRebindTarget(
          provider = provider,
          threadId = candidate.threadId,
          title = candidate.title,
          activity = candidate.activity,
          updatedAt = candidate.updatedAt,
        )
      }
      val bindings = matchConcreteCodexTabs(
        concreteTabs = concreteTabs,
        candidates = candidates,
        unavailableThreadIdentities = unavailableThreadIdentities,
      )
      if (bindings.isEmpty()) {
        continue
      }
      val requests = ArrayList<AgentChatConcreteCodexTabRebindRequest>(bindings.size)
      for (binding in bindings) {
        requests.add(
          AgentChatConcreteCodexTabRebindRequest(
            tabKey = binding.tabKey,
            currentThreadIdentity = binding.currentThreadIdentity,
            newThreadRebindRequestedAtMs = binding.newThreadRebindRequestedAtMs,
            target = binding.target,
          )
        )
        unavailableThreadIdentities.add(binding.target.threadIdentity)
      }
      requestsByPath[path] = requests
    }

    if (requestsByPath.isEmpty()) {
      return
    }

    val rebindReport = withContext(Dispatchers.UI) {
      openAgentChatConcreteTabsBinder(provider, requestsByPath)
    }

    LOG.debug {
      "Provider refresh id=$refreshId provider=codex rebound concrete /new chat tabs " +
      "(reboundBindings=${rebindReport.reboundBindings}, reboundFiles=${rebindReport.reboundFiles}, " +
      "requestedBindings=${rebindReport.requestedBindings}, candidatePaths=${refreshHintsByPath.size}, matchedPaths=${requestsByPath.size})"
    }
  }

  private fun selectPendingTabsEligibleForRebind(
    pendingTabsByPath: Map<String, List<AgentChatPendingCodexTabSnapshot>>,
    allowedThreadIdsByPath: Map<String, Set<String>>?,
    nowMs: Long,
  ): Map<String, List<AgentChatPendingCodexTabSnapshot>> {
    if (pendingTabsByPath.isEmpty()) {
      return emptyMap()
    }

    if (allowedThreadIdsByPath == null) {
      return pendingTabsByPath.filterValues { pendingTabs -> pendingTabs.isNotEmpty() }
    }

    val eligibleByPath = LinkedHashMap<String, List<AgentChatPendingCodexTabSnapshot>>()
    for ((path, pendingTabs) in pendingTabsByPath) {
      if (pendingTabs.isEmpty()) {
        continue
      }

      val eligibleTabs = if (allowedThreadIdsByPath.containsKey(path)) {
        pendingTabs
      }
      else {
        pendingTabs.filter { pendingTab ->
          pendingTab.isEligibleForNoBaselineAutoBind(nowMs)
        }
      }
      if (eligibleTabs.isNotEmpty()) {
        eligibleByPath[path] = eligibleTabs
      }
    }
    return eligibleByPath
  }

  fun mergePendingThreadsFromOpenTabs(
    outcomes: MutableMap<String, ProviderRefreshOutcome>,
    targetPaths: Set<String>,
    refreshId: Long,
    pendingTabsByPath: Map<String, List<AgentChatPendingCodexTabSnapshot>>,
  ): Set<String> {
    if (pendingTabsByPath.isEmpty() || outcomes.isEmpty()) {
      return emptySet()
    }

    val normalizedTargetPaths = targetPaths
      .asSequence()
      .map(::normalizeAgentWorkbenchPath)
      .toHashSet()
    val outcomePathByNormalizedPath = LinkedHashMap<String, String>()
    outcomes.keys.forEach { path ->
      outcomePathByNormalizedPath.putIfAbsent(normalizeAgentWorkbenchPath(path), path)
    }

    val projectedPaths = LinkedHashSet<String>()
    var projectedThreads = 0
    for ((path, pendingTabs) in pendingTabsByPath) {
      val normalizedPath = normalizeAgentWorkbenchPath(path)
      if (normalizedPath !in normalizedTargetPaths) {
        continue
      }

      val outcomePath = outcomePathByNormalizedPath[normalizedPath] ?: continue
      val pendingThreads = buildPendingCodexThreads(pendingTabs)
      if (pendingThreads.isEmpty()) {
        continue
      }

      val existingOutcome = outcomes[outcomePath] ?: ProviderRefreshOutcome()
      val mergedThreads = mergeProviderThreadsWithPendingCodex(
        sourceThreads = existingOutcome.threads.orEmpty(),
        pendingThreads = pendingThreads,
      )
      outcomes[outcomePath] = existingOutcome.copy(threads = mergedThreads)
      projectedPaths += normalizedPath
      projectedThreads += pendingThreads.size
    }

    if (projectedPaths.isNotEmpty()) {
      LOG.debug {
        "Provider refresh id=$refreshId provider=codex projected pending rows " +
        "(paths=${projectedPaths.size}, threads=$projectedThreads)"
      }
    }

    return projectedPaths
  }

  private fun buildPendingCodexThreads(
    pendingTabs: List<AgentChatPendingCodexTabSnapshot>,
  ): List<AgentSessionThread> {
    val threadsById = LinkedHashMap<String, AgentSessionThread>()
    for (pendingTab in pendingTabs) {
      val identity = parseAgentSessionIdentity(pendingTab.pendingThreadIdentity) ?: continue
      if (identity.provider != provider) continue
      if (!isAgentSessionNewSessionId(identity.sessionId)) continue

      val updatedAt = pendingTab.pendingFirstInputAtMs ?: pendingTab.pendingCreatedAtMs ?: 0L
      val pendingThread = AgentSessionThread(
        id = identity.sessionId,
        title = AgentSessionsBundle.message("toolwindow.action.new.thread"),
        updatedAt = updatedAt,
        archived = false,
        activity = AgentThreadActivity.READY,
        provider = provider,
      )
      val existing = threadsById[identity.sessionId]
      if (existing == null || pendingThread.updatedAt > existing.updatedAt) {
        threadsById[identity.sessionId] = pendingThread
      }
    }
    return threadsById.values.toList()
  }

  private fun mergeProviderThreadsWithPendingCodex(
    sourceThreads: List<AgentSessionThread>,
    pendingThreads: List<AgentSessionThread>,
  ): List<AgentSessionThread> {
    if (pendingThreads.isEmpty()) {
      return sourceThreads
    }

    val threadsById = LinkedHashMap<String, AgentSessionThread>(sourceThreads.size + pendingThreads.size)
    sourceThreads.forEach { thread ->
      threadsById[thread.id] = thread
    }
    pendingThreads.forEach { pendingThread ->
      val existing = threadsById[pendingThread.id]
      threadsById[pendingThread.id] = if (existing == null || pendingThread.updatedAt >= existing.updatedAt) {
        pendingThread
      }
      else {
        existing
      }
    }
    return threadsById.values.sortedByDescending { thread -> thread.updatedAt }
  }

  private fun reconcilePendingTabsForProjection(
    pendingTabsByPath: Map<String, List<AgentChatPendingCodexTabSnapshot>>,
    rebindReport: AgentChatPendingCodexTabRebindReport,
  ): Map<String, List<AgentChatPendingCodexTabSnapshot>> {
    val staleRefsByPath = collectStalePendingTabRefsByPath(rebindReport)
    if (staleRefsByPath.isEmpty()) {
      return pendingTabsByPath
    }

    val reconciled = LinkedHashMap<String, List<AgentChatPendingCodexTabSnapshot>>(pendingTabsByPath.size)
    for ((path, pendingTabs) in pendingTabsByPath) {
      val staleRefs = staleRefsByPath[path]
      if (staleRefs.isNullOrEmpty()) {
        reconciled[path] = pendingTabs
        continue
      }
      val filtered = pendingTabs.filterNot { pendingTab ->
        PendingCodexTabRef(
          pendingTabKey = pendingTab.pendingTabKey,
          pendingThreadIdentity = pendingTab.pendingThreadIdentity,
        ) in staleRefs
      }
      if (filtered.isNotEmpty()) {
        reconciled[path] = filtered
      }
    }
    return reconciled
  }

  private fun collectStalePendingTabRefsByPath(
    rebindReport: AgentChatPendingCodexTabRebindReport,
  ): Map<String, Set<PendingCodexTabRef>> {
    if (rebindReport.outcomesByPath.isEmpty()) {
      return emptyMap()
    }

    val staleRefsByPath = LinkedHashMap<String, LinkedHashSet<PendingCodexTabRef>>()
    for ((path, outcomes) in rebindReport.outcomesByPath) {
      val normalizedPath = normalizeAgentWorkbenchPath(path)
      for (outcome in outcomes) {
        if (!outcome.status.shouldDropFromPendingProjection()) {
          continue
        }
        staleRefsByPath
          .getOrPut(normalizedPath) { LinkedHashSet() }
          .add(
            PendingCodexTabRef(
              pendingTabKey = outcome.request.pendingTabKey,
              pendingThreadIdentity = outcome.request.pendingThreadIdentity,
            )
          )
      }
    }
    return staleRefsByPath
  }

  private fun clearPendingCodexAmbiguityState() {
    synchronized(pendingCodexAmbiguityLock) {
      pendingCodexAmbiguityStateByKey.clear()
    }
  }

  private fun reportPendingCodexMatchingGaps(
    refreshId: Long,
    ambiguousByPath: Map<String, Set<String>>,
    noMatchByPath: Map<String, Set<String>>,
  ) {
    val trackedKeys = LinkedHashSet<String>()
    val now = System.currentTimeMillis()

    for ((path, pendingIdentities) in ambiguousByPath) {
      for (pendingIdentity in pendingIdentities) {
        val key = "$path|$pendingIdentity"
        trackedKeys.add(key)

        var shouldWarn = false
        synchronized(pendingCodexAmbiguityLock) {
          val previous = pendingCodexAmbiguityStateByKey[key]
          val nextPollCount = (previous?.pollCount ?: 0) + 1
          val lastWarnedAtMs = previous?.lastWarnedAtMs
          if (
            nextPollCount >= PENDING_CODEX_AMBIGUITY_NOTIFY_AFTER_POLLS &&
            (lastWarnedAtMs == null || now - lastWarnedAtMs >= PENDING_CODEX_AMBIGUITY_NOTIFY_COOLDOWN_MS)
          ) {
            shouldWarn = true
            pendingCodexAmbiguityStateByKey[key] = PendingCodexAmbiguityState(
              pollCount = nextPollCount,
              lastWarnedAtMs = now,
            )
          }
          else {
            pendingCodexAmbiguityStateByKey[key] = PendingCodexAmbiguityState(
              pollCount = nextPollCount,
              lastWarnedAtMs = lastWarnedAtMs,
            )
          }
        }

        if (shouldWarn) {
          LOG.warn(
            "Provider refresh id=$refreshId provider=codex skipped ambiguous pending tab binding for path=$path, " +
            "pendingIdentity=$pendingIdentity. Use editor tab action 'Bind Pending Codex Thread'."
          )
        }
      }
    }

    for ((path, pendingIdentities) in noMatchByPath) {
      for (pendingIdentity in pendingIdentities) {
        trackedKeys.add("$path|$pendingIdentity")
      }
    }

    synchronized(pendingCodexAmbiguityLock) {
      pendingCodexAmbiguityStateByKey.keys.retainAll(trackedKeys)
    }
  }

  private fun buildCodexRebindTarget(
    provider: AgentSessionProvider,
    threadId: String,
    title: String,
    activity: AgentThreadActivity,
    updatedAt: Long,
  ): AgentChatTabRebindTarget {
    val launchSpec = runCatching {
      buildAgentSessionResumeLaunchSpec(provider, threadId)
    }.getOrDefault(AgentSessionTerminalLaunchSpec(command = listOf(provider.value, "resume", threadId)))
    return AgentChatTabRebindTarget(
      threadIdentity = buildAgentSessionIdentity(provider, threadId),
      threadId = threadId,
      shellCommand = launchSpec.command,
      shellEnvVariables = launchSpec.envVariables,
      threadTitle = title,
      threadActivity = activity,
      threadUpdatedAt = updatedAt,
    )
  }
}

@Suppress("DuplicatedCode")
private fun matchConcreteCodexTabs(
  concreteTabs: List<AgentChatConcreteCodexTabSnapshot>,
  candidates: List<AgentChatTabRebindTarget>,
  unavailableThreadIdentities: Set<String>,
): List<ConcreteCodexTabBinding> {
  if (concreteTabs.isEmpty() || candidates.isEmpty()) {
    return emptyList()
  }

  val uniqueConcreteTabs = concreteTabs
    .asSequence()
    .distinctBy { it.tabKey }
    .toList()
  val concreteTabByKey = uniqueConcreteTabs.associateBy { it.tabKey }
  val candidateByIdentity = deduplicateRebindTargets(candidates)
    .filterKeys { it !in unavailableThreadIdentities }
  val concreteEdges = LinkedHashMap<String, LinkedHashSet<String>>(uniqueConcreteTabs.size)
  val candidateEdges = LinkedHashMap<String, LinkedHashSet<String>>(candidateByIdentity.size)

  for (concreteTab in uniqueConcreteTabs) {
    val minTimestamp = concreteTab.newThreadRebindRequestedAtMs - CONCRETE_CODEX_NEW_THREAD_MATCH_PRE_WINDOW_MS
    val maxTimestamp = concreteTab.newThreadRebindRequestedAtMs + CONCRETE_CODEX_NEW_THREAD_MATCH_POST_WINDOW_MS
    val connectedCandidates = LinkedHashSet<String>()
    for ((candidateIdentity, candidate) in candidateByIdentity) {
      val updatedAt = candidate.threadUpdatedAt
      if (updatedAt <= 0L) {
        continue
      }
      if (updatedAt in minTimestamp..maxTimestamp) {
        connectedCandidates.add(candidateIdentity)
        candidateEdges.getOrPut(candidateIdentity) { LinkedHashSet() }.add(concreteTab.tabKey)
      }
    }
    concreteEdges[concreteTab.tabKey] = connectedCandidates
  }

  val bindings = LinkedHashMap<String, AgentChatTabRebindTarget>()
  while (true) {
    val forcedPairs = concreteEdges.entries
      .asSequence()
      .filter { it.value.size == 1 }
      .map { it.key to it.value.first() }
      .filter { (tabKey, candidateIdentity) ->
        candidateEdges[candidateIdentity]?.let { tabKeys -> tabKeys.size == 1 && tabKey in tabKeys } == true
      }
      .toList()
    if (forcedPairs.isEmpty()) {
      break
    }

    for ((tabKey, candidateIdentity) in forcedPairs) {
      if (tabKey !in concreteEdges || candidateIdentity !in candidateEdges) {
        continue
      }
      val target = candidateByIdentity[candidateIdentity] ?: continue
      bindings[tabKey] = target

      concreteEdges.remove(tabKey)
      candidateEdges.remove(candidateIdentity)
      concreteEdges.values.forEach { it.remove(candidateIdentity) }
      candidateEdges.values.forEach { it.remove(tabKey) }
    }
  }

  return bindings.entries
    .sortedBy { it.key }
    .mapNotNull { (tabKey, target) ->
      val concreteTab = concreteTabByKey[tabKey] ?: return@mapNotNull null
      ConcreteCodexTabBinding(
        tabKey = concreteTab.tabKey,
        currentThreadIdentity = concreteTab.currentThreadIdentity,
        newThreadRebindRequestedAtMs = concreteTab.newThreadRebindRequestedAtMs,
        target = target,
      )
    }
}

private fun deduplicateRebindTargets(candidates: List<AgentChatTabRebindTarget>): Map<String, AgentChatTabRebindTarget> {
  val result = LinkedHashMap<String, AgentChatTabRebindTarget>()
  for (candidate in candidates) {
    val existing = result[candidate.threadIdentity]
    if (existing == null || candidate.threadUpdatedAt >= existing.threadUpdatedAt) {
      result[candidate.threadIdentity] = candidate
    }
  }
  return result
}

private data class PendingCodexAmbiguityState(
  @JvmField val pollCount: Int,
  @JvmField val lastWarnedAtMs: Long?,
)

private data class ConcreteCodexTabBinding(
  val tabKey: String,
  val currentThreadIdentity: String,
  val newThreadRebindRequestedAtMs: Long,
  val target: AgentChatTabRebindTarget,
)

private data class PendingCodexTabRef(
  val pendingTabKey: String,
  val pendingThreadIdentity: String,
)

private fun AgentChatPendingCodexTabRebindStatus.shouldDropFromPendingProjection(): Boolean {
  return this == AgentChatPendingCodexTabRebindStatus.REBOUND ||
         this == AgentChatPendingCodexTabRebindStatus.PENDING_TAB_NOT_OPEN ||
         this == AgentChatPendingCodexTabRebindStatus.INVALID_PENDING_TAB
}

private fun AgentChatPendingCodexTabSnapshot.isEligibleForNoBaselineAutoBind(nowMs: Long): Boolean {
  val createdAtMs = pendingCreatedAtMs ?: return false
  if (pendingLaunchMode.isNullOrBlank()) {
    return false
  }
  return nowMs >= createdAtMs && nowMs - createdAtMs <= PENDING_CODEX_NO_BASELINE_AUTO_BIND_MAX_AGE_MS
}
