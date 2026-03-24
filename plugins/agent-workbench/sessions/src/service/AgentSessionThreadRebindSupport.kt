// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatConcreteTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatConcreteTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatConcreteTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatConcreteTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionRefreshHints
import com.intellij.agent.workbench.sessions.util.buildAgentSessionIdentity
import com.intellij.agent.workbench.sessions.util.isAgentSessionNewSessionId
import com.intellij.agent.workbench.sessions.util.parseAgentSessionIdentity
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = logger<AgentSessionThreadRebindSupport>()
private const val PENDING_THREAD_MATCH_PRE_WINDOW_MS = 20_000L
private const val PENDING_THREAD_MATCH_POST_WINDOW_MS = 120_000L
private const val PENDING_THREAD_NO_BASELINE_AUTO_BIND_MAX_AGE_MS = PENDING_THREAD_MATCH_POST_WINDOW_MS
private const val PENDING_THREAD_AMBIGUITY_NOTIFY_AFTER_POLLS = 2
private const val PENDING_THREAD_AMBIGUITY_NOTIFY_COOLDOWN_MS = 5 * 60 * 1000L
private const val CONCRETE_CODEX_NEW_THREAD_MATCH_PRE_WINDOW_MS = 5_000L
private const val CONCRETE_CODEX_NEW_THREAD_MATCH_POST_WINDOW_MS = 30_000L
private const val CONCRETE_CODEX_NEW_THREAD_REBIND_MAX_AGE_MS = CONCRETE_CODEX_NEW_THREAD_MATCH_POST_WINDOW_MS
private const val PROVIDER_REFRESH_HINT_MAX_LISTED_THREAD_IDS_PER_PATH = 200

internal data class PendingTabBindOutcome(
    val pendingTabsForProjectionByPath: Map<String, List<AgentChatPendingTabSnapshot>>,
)

internal class AgentSessionThreadRebindSupport(
  private val provider: AgentSessionProvider,
  private val openAgentChatPendingTabsBinder: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport,
  private val openAgentChatConcreteTabsBinder: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteTabRebindRequest>>,
  ) -> AgentChatConcreteTabRebindReport,
  private val clearOpenConcreteNewThreadRebindAnchors: (
    AgentSessionProvider,
    Map<String, List<AgentChatConcreteTabSnapshot>>,
  ) -> Int,
) {
  private val pendingThreadAmbiguityLock = Any()
  private val pendingThreadAmbiguityStateByKey = LinkedHashMap<String, PendingThreadAmbiguityState>()

  fun collectRefreshHintThreadIdsByPath(
    targetPaths: Set<String>,
    outcomes: Map<String, ProviderRefreshOutcome>,
    knownThreadIdsByPath: Map<String, Set<String>>,
    pendingTabsByPath: Map<String, List<AgentChatPendingTabSnapshot>>,
    concreteTabsByPath: Map<String, List<AgentChatConcreteTabSnapshot>> = emptyMap(),
    openConcreteThreadIdentitiesByPath: Map<String, Set<String>> = emptyMap(),
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
        .take(PROVIDER_REFRESH_HINT_MAX_LISTED_THREAD_IDS_PER_PATH)
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
      pendingTabsByPath: Map<String, List<AgentChatPendingTabSnapshot>> = emptyMap(),
      openConcreteThreadIdentitiesByPath: Map<String, Set<String>> = emptyMap(),
  ): PendingTabBindOutcome {
    if (pendingTabsByPath.isEmpty()) {
      clearPendingThreadAmbiguityState()
      return PendingTabBindOutcome(emptyMap())
    }
    val eligiblePendingTabsByPath = selectPendingTabsEligibleForRebind(
      pendingTabsByPath = pendingTabsByPath,
      allowedThreadIdsByPath = allowedThreadIdsByPath,
      nowMs = System.currentTimeMillis(),
    )
    if (eligiblePendingTabsByPath.isEmpty()) {
      clearPendingThreadAmbiguityState()
      return PendingTabBindOutcome(pendingTabsForProjectionByPath = pendingTabsByPath)
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
          buildRebindTarget(
            path = path,
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
          buildRebindTarget(
            path = path,
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
      return PendingTabBindOutcome(pendingTabsForProjectionByPath = pendingTabsByPath)
    }

    val matchResult = PendingAgentChatTabMatcher.match(
      pendingTabsByPath = eligiblePendingTabsByPath,
      candidatesByPath = candidatesByPath,
      openConcreteIdentitiesByPath = openConcreteThreadIdentitiesByPath,
      preWindowMs = PENDING_THREAD_MATCH_PRE_WINDOW_MS,
      postWindowMs = PENDING_THREAD_MATCH_POST_WINDOW_MS,
    )

    reportPendingThreadMatchingGaps(
      refreshId = refreshId,
      ambiguousByPath = matchResult.ambiguousPendingThreadIdentitiesByPath,
      noMatchByPath = matchResult.noMatchPendingThreadIdentitiesByPath,
    )

    val bindingsByPath = matchResult.bindingsByPath
    if (bindingsByPath.isEmpty()) {
      return PendingTabBindOutcome(pendingTabsForProjectionByPath = pendingTabsByPath)
    }

    val requestsByPath = LinkedHashMap<String, List<AgentChatPendingTabRebindRequest>>(bindingsByPath.size)
    for ((path, bindings) in bindingsByPath) {
      requestsByPath[path] = bindings.map { binding ->
        AgentChatPendingTabRebindRequest(
          pendingTabKey = binding.pendingTabKey,
          pendingThreadIdentity = binding.pendingThreadIdentity,
          target = binding.target,
        )
      }
    }

    val rebindReport = openAgentChatPendingTabsBinder(provider, requestsByPath)

    LOG.debug {
      "Provider refresh id=$refreshId provider=${provider.value} rebound pending chat tabs " +
      "(reboundBindings=${rebindReport.reboundBindings}, reboundFiles=${rebindReport.reboundFiles}, " +
      "requestedBindings=${rebindReport.requestedBindings}, candidatePaths=${candidatesByPath.size}, matchedPaths=${bindingsByPath.size})"
    }

    return PendingTabBindOutcome(
      pendingTabsForProjectionByPath = reconcilePendingTabsForProjection(
        pendingTabsByPath = pendingTabsByPath,
        rebindReport = rebindReport,
      ),
    )
  }

  suspend fun bindConcreteOpenChatTabsAwaitingNewThread(
    refreshId: Long,
    refreshHintsByPath: Map<String, AgentSessionRefreshHints>,
    concreteTabsByPath: Map<String, List<AgentChatConcreteTabSnapshot>> = emptyMap(),
    openConcreteThreadIdentitiesByPath: MutableMap<String, MutableSet<String>> = linkedMapOf(),
  ) {
    if (concreteTabsByPath.isEmpty()) {
      return
    }

    val nowMs = System.currentTimeMillis()
    val staleTabsByPath = LinkedHashMap<String, List<AgentChatConcreteTabSnapshot>>()
    val eligibleTabsByPath = LinkedHashMap<String, List<AgentChatConcreteTabSnapshot>>()
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
        clearOpenConcreteNewThreadRebindAnchors(provider, staleTabsByPath)
      }
      LOG.debug {
        "Provider refresh id=$refreshId provider=${provider.value} cleared stale /new tab anchors " +
        "(paths=${staleTabsByPath.size}, cleared=$cleared)"
      }
    }

    if (eligibleTabsByPath.isEmpty() || refreshHintsByPath.isEmpty()) {
      return
    }

    val unavailableThreadIdentitiesByPath = LinkedHashMap<String, LinkedHashSet<String>>(openConcreteThreadIdentitiesByPath.size)
    for ((path, threadIdentities) in openConcreteThreadIdentitiesByPath) {
      unavailableThreadIdentitiesByPath[normalizeAgentWorkbenchPath(path)] = LinkedHashSet(threadIdentities)
    }

    val requestsByPath = LinkedHashMap<String, List<AgentChatConcreteTabRebindRequest>>()
    for ((path, concreteTabs) in eligibleTabsByPath) {
      val rebindCandidates = refreshHintsByPath[path]?.rebindCandidates.orEmpty()
      if (rebindCandidates.isEmpty()) {
        continue
      }
      val unavailableThreadIdentities = unavailableThreadIdentitiesByPath.getOrPut(path) { LinkedHashSet() }
      val candidates = rebindCandidates.map { candidate ->
        buildRebindTarget(
          path = path,
          provider = provider,
          threadId = candidate.threadId,
          title = candidate.title,
          activity = candidate.activity,
          updatedAt = candidate.updatedAt,
        )
      }
      val bindings = matchConcreteNewThreadTabs(
        concreteTabs = concreteTabs,
        candidates = candidates,
        unavailableThreadIdentities = unavailableThreadIdentities,
      )
      if (bindings.isEmpty()) {
        continue
      }
      val requests = ArrayList<AgentChatConcreteTabRebindRequest>(bindings.size)
      for (binding in bindings) {
        requests.add(
          AgentChatConcreteTabRebindRequest(
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

    val rebindReport = openAgentChatConcreteTabsBinder(provider, requestsByPath)
    applyConcreteRebindsToOpenIdentities(
      openConcreteThreadIdentitiesByPath = openConcreteThreadIdentitiesByPath,
      rebindReport = rebindReport,
    )

    LOG.debug {
      "Provider refresh id=$refreshId provider=${provider.value} rebound concrete /new chat tabs " +
      "(reboundBindings=${rebindReport.reboundBindings}, reboundFiles=${rebindReport.reboundFiles}, " +
      "requestedBindings=${rebindReport.requestedBindings}, candidatePaths=${refreshHintsByPath.size}, matchedPaths=${requestsByPath.size})"
    }
  }

  private fun applyConcreteRebindsToOpenIdentities(
    openConcreteThreadIdentitiesByPath: MutableMap<String, MutableSet<String>>,
    rebindReport: AgentChatConcreteTabRebindReport,
  ) {
    if (rebindReport.outcomesByPath.isEmpty()) {
      return
    }

    for ((path, outcomes) in rebindReport.outcomesByPath) {
      val normalizedPath = normalizeAgentWorkbenchPath(path)
      val openThreadIdentities = openConcreteThreadIdentitiesByPath.getOrPut(normalizedPath) { LinkedHashSet() }
      for (outcome in outcomes) {
        if (outcome.status != AgentChatConcreteTabRebindStatus.REBOUND) {
          continue
        }
        openThreadIdentities.remove(outcome.request.currentThreadIdentity)
        openThreadIdentities.add(outcome.request.target.threadIdentity)
      }
    }
  }

  private fun selectPendingTabsEligibleForRebind(
      pendingTabsByPath: Map<String, List<AgentChatPendingTabSnapshot>>,
      allowedThreadIdsByPath: Map<String, Set<String>>?,
      nowMs: Long,
  ): Map<String, List<AgentChatPendingTabSnapshot>> {
    if (pendingTabsByPath.isEmpty()) {
      return emptyMap()
    }

    if (allowedThreadIdsByPath == null) {
      return pendingTabsByPath.filterValues { pendingTabs -> pendingTabs.isNotEmpty() }
    }

    val eligibleByPath = LinkedHashMap<String, List<AgentChatPendingTabSnapshot>>()
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
      pendingTabsByPath: Map<String, List<AgentChatPendingTabSnapshot>>,
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
      val pendingThreads = buildPendingThreads(pendingTabs)
      if (pendingThreads.isEmpty()) {
        continue
      }

      val existingOutcome = outcomes[outcomePath] ?: ProviderRefreshOutcome()
      val mergedThreads = mergeProviderThreadsWithPendingThreads(
        sourceThreads = existingOutcome.threads.orEmpty(),
        pendingThreads = pendingThreads,
      )
      outcomes[outcomePath] = existingOutcome.copy(threads = mergedThreads)
      projectedPaths += normalizedPath
      projectedThreads += pendingThreads.size
    }

    if (projectedPaths.isNotEmpty()) {
      LOG.debug {
        "Provider refresh id=$refreshId provider=${provider.value} projected pending rows " +
        "(paths=${projectedPaths.size}, threads=$projectedThreads)"
      }
    }

    return projectedPaths
  }

  private fun buildPendingThreads(
      pendingTabs: List<AgentChatPendingTabSnapshot>,
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

  private fun mergeProviderThreadsWithPendingThreads(
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
    pendingTabsByPath: Map<String, List<AgentChatPendingTabSnapshot>>,
    rebindReport: AgentChatPendingTabRebindReport,
  ): Map<String, List<AgentChatPendingTabSnapshot>> {
    val staleRefsByPath = collectStalePendingTabRefsByPath(rebindReport)
    if (staleRefsByPath.isEmpty()) {
      return pendingTabsByPath
    }

    val reconciled = LinkedHashMap<String, List<AgentChatPendingTabSnapshot>>(pendingTabsByPath.size)
    for ((path, pendingTabs) in pendingTabsByPath) {
      val staleRefs = staleRefsByPath[path]
      if (staleRefs.isNullOrEmpty()) {
        reconciled[path] = pendingTabs
        continue
      }
      val filtered = pendingTabs.filterNot { pendingTab ->
        PendingTabRef(
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
    rebindReport: AgentChatPendingTabRebindReport,
  ): Map<String, Set<PendingTabRef>> {
    if (rebindReport.outcomesByPath.isEmpty()) {
      return emptyMap()
    }

    val staleRefsByPath = LinkedHashMap<String, LinkedHashSet<PendingTabRef>>()
    for ((path, outcomes) in rebindReport.outcomesByPath) {
      val normalizedPath = normalizeAgentWorkbenchPath(path)
      for (outcome in outcomes) {
        if (!outcome.status.shouldDropFromPendingProjection()) {
          continue
        }
        staleRefsByPath
          .getOrPut(normalizedPath) { LinkedHashSet() }
          .add(
            PendingTabRef(
              pendingTabKey = outcome.request.pendingTabKey,
              pendingThreadIdentity = outcome.request.pendingThreadIdentity,
            )
          )
      }
    }
    return staleRefsByPath
  }

  private fun clearPendingThreadAmbiguityState() {
    synchronized(pendingThreadAmbiguityLock) {
      pendingThreadAmbiguityStateByKey.clear()
    }
  }

  private fun reportPendingThreadMatchingGaps(
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
        synchronized(pendingThreadAmbiguityLock) {
          val previous = pendingThreadAmbiguityStateByKey[key]
          val nextPollCount = (previous?.pollCount ?: 0) + 1
          val lastWarnedAtMs = previous?.lastWarnedAtMs
          if (
            nextPollCount >= PENDING_THREAD_AMBIGUITY_NOTIFY_AFTER_POLLS &&
            (lastWarnedAtMs == null || now - lastWarnedAtMs >= PENDING_THREAD_AMBIGUITY_NOTIFY_COOLDOWN_MS)
          ) {
            shouldWarn = true
            pendingThreadAmbiguityStateByKey[key] = PendingThreadAmbiguityState(
              pollCount = nextPollCount,
              lastWarnedAtMs = now,
            )
          }
          else {
            pendingThreadAmbiguityStateByKey[key] = PendingThreadAmbiguityState(
              pollCount = nextPollCount,
              lastWarnedAtMs = lastWarnedAtMs,
            )
          }
        }

        if (shouldWarn) {
          LOG.warn(
            "Provider refresh id=$refreshId provider=${provider.value} skipped ambiguous pending tab binding for path=$path, " +
            "pendingIdentity=$pendingIdentity. Use editor tab action 'Bind Pending Thread'."
          )
        }
      }
    }

    for ((path, pendingIdentities) in noMatchByPath) {
      for (pendingIdentity in pendingIdentities) {
        trackedKeys.add("$path|$pendingIdentity")
      }
    }

    synchronized(pendingThreadAmbiguityLock) {
      pendingThreadAmbiguityStateByKey.keys.retainAll(trackedKeys)
    }
  }

  private fun buildRebindTarget(
    path: String,
    provider: AgentSessionProvider,
    threadId: String,
    title: String,
    activity: AgentThreadActivity,
    updatedAt: Long,
  ): AgentChatTabRebindTarget {
    return AgentChatTabRebindTarget(
      projectPath = normalizeAgentWorkbenchPath(path),
      provider = provider,
      threadIdentity = buildAgentSessionIdentity(provider, threadId),
      threadId = threadId,
      threadTitle = title,
      threadActivity = activity,
      threadUpdatedAt = updatedAt,
    )
  }
}

@Suppress("DuplicatedCode")
private fun matchConcreteNewThreadTabs(
  concreteTabs: List<AgentChatConcreteTabSnapshot>,
  candidates: List<AgentChatTabRebindTarget>,
  unavailableThreadIdentities: Set<String>,
): List<ConcreteNewThreadTabBinding> {
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
      ConcreteNewThreadTabBinding(
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

private data class PendingThreadAmbiguityState(
  @JvmField val pollCount: Int,
  @JvmField val lastWarnedAtMs: Long?,
)

private data class ConcreteNewThreadTabBinding(
  val tabKey: String,
  val currentThreadIdentity: String,
  val newThreadRebindRequestedAtMs: Long,
  val target: AgentChatTabRebindTarget,
)

private data class PendingTabRef(
  val pendingTabKey: String,
  val pendingThreadIdentity: String,
)

private fun AgentChatPendingTabRebindStatus.shouldDropFromPendingProjection(): Boolean {
  return this == AgentChatPendingTabRebindStatus.REBOUND ||
         this == AgentChatPendingTabRebindStatus.PENDING_TAB_NOT_OPEN ||
         this == AgentChatPendingTabRebindStatus.INVALID_PENDING_TAB
}

private fun AgentChatPendingTabSnapshot.isEligibleForNoBaselineAutoBind(nowMs: Long): Boolean {
  val createdAtMs = pendingCreatedAtMs ?: return false
  if (pendingLaunchMode.isNullOrBlank()) {
    return false
  }
  return nowMs >= createdAtMs && nowMs - createdAtMs <= PENDING_THREAD_NO_BASELINE_AUTO_BIND_MAX_AGE_MS
}
