// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabRebindStatus
import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindTarget
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
private const val CODEX_REFRESH_HINT_MAX_LISTED_THREAD_IDS_PER_PATH = 200

internal data class PendingCodexBindOutcome(
  val pendingTabsForProjectionByPath: Map<String, List<AgentChatPendingCodexTabSnapshot>>,
)

internal class AgentSessionCodexRefreshSupport(
  private val openPendingCodexTabsProvider: suspend () -> Map<String, List<AgentChatPendingCodexTabSnapshot>>,
  private val openConcreteChatThreadIdentitiesByPathProvider: suspend () -> Map<String, Set<String>>,
  private val openAgentChatPendingTabsBinder: (
    Map<String, List<AgentChatPendingCodexTabRebindRequest>>,
  ) -> AgentChatPendingCodexTabRebindReport,
) {
  private val pendingCodexAmbiguityLock = Any()
  private val pendingCodexAmbiguityStateByKey = LinkedHashMap<String, PendingCodexAmbiguityState>()

  suspend fun collectNormalizedPendingTabsByPath(): Map<String, List<AgentChatPendingCodexTabSnapshot>> {
    val pendingTabsByPath = try {
      openPendingCodexTabsProvider()
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

  suspend fun collectRefreshHintThreadIdsByPath(
    targetPaths: Set<String>,
    outcomes: Map<String, ProviderRefreshOutcome>,
    knownThreadIdsByPath: Map<String, Set<String>>,
    pendingTabsByPath: Map<String, List<AgentChatPendingCodexTabSnapshot>>,
  ): Map<String, Set<String>> {
    if (targetPaths.isEmpty()) {
      return emptyMap()
    }

    val hintThreadIdsByPath = LinkedHashMap<String, LinkedHashSet<String>>()
    for (path in targetPaths) {
      val ids = LinkedHashSet<String>()
      knownThreadIdsByPath[path]
        .orEmpty()
        .asSequence()
        .filterNot(::isAgentSessionNewSessionId)
        .forEach(ids::add)

      outcomes[path]
        ?.threads
        .orEmpty()
        .asSequence()
        .filter { thread -> thread.provider == AgentSessionProvider.CODEX }
        .sortedByDescending { thread -> thread.updatedAt }
        .take(CODEX_REFRESH_HINT_MAX_LISTED_THREAD_IDS_PER_PATH)
        .mapTo(ids) { thread -> thread.id }

      pendingTabsByPath[path]
        .orEmpty()
        .forEach { pendingTab ->
          val identity = parseAgentSessionIdentity(pendingTab.pendingThreadIdentity) ?: return@forEach
          if (identity.provider != AgentSessionProvider.CODEX || isAgentSessionNewSessionId(identity.sessionId)) {
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
        if (identity.provider != AgentSessionProvider.CODEX || isAgentSessionNewSessionId(identity.sessionId)) {
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
        if (thread.provider != AgentSessionProvider.CODEX) {
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
    val provider = AgentSessionProvider.CODEX
    val eligiblePendingTabsByPath = selectPendingTabsEligibleForRebind(
      pendingTabsByPath = pendingTabsByPath,
      allowedThreadIdsByPath = allowedThreadIdsByPath,
      nowMs = System.currentTimeMillis(),
    )
    if (eligiblePendingTabsByPath.isEmpty()) {
      clearPendingCodexAmbiguityState()
      return PendingCodexBindOutcome(pendingTabsForProjectionByPath = pendingTabsByPath)
    }

    val candidatesByPath = LinkedHashMap<String, MutableList<AgentChatPendingTabRebindTarget>>()
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
          buildPendingCodexRebindTarget(
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
          buildPendingCodexRebindTarget(
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
      openAgentChatPendingTabsBinder(requestsByPath)
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
      if (identity.provider != AgentSessionProvider.CODEX) continue
      if (!isAgentSessionNewSessionId(identity.sessionId)) continue

      val updatedAt = pendingTab.pendingFirstInputAtMs ?: pendingTab.pendingCreatedAtMs ?: 0L
      val pendingThread = AgentSessionThread(
        id = identity.sessionId,
        title = AgentSessionsBundle.message("toolwindow.action.new.thread"),
        updatedAt = updatedAt,
        archived = false,
        activity = AgentThreadActivity.READY,
        provider = AgentSessionProvider.CODEX,
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

  private fun buildPendingCodexRebindTarget(
    provider: AgentSessionProvider,
    threadId: String,
    title: String,
    activity: AgentThreadActivity,
    updatedAt: Long,
  ): AgentChatPendingTabRebindTarget {
    val launchSpec = runCatching {
      buildAgentSessionResumeLaunchSpec(provider, threadId)
    }.getOrDefault(AgentSessionTerminalLaunchSpec(command = listOf(provider.value, "resume", threadId)))
    return AgentChatPendingTabRebindTarget(
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

private data class PendingCodexAmbiguityState(
  @JvmField val pollCount: Int,
  @JvmField val lastWarnedAtMs: Long?,
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
