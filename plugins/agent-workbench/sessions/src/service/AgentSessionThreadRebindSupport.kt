// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatConcreteTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindReport
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindRequest
import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget
import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy.isConcreteCodexNewThreadRebindAnchorActive
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy.PENDING_THREAD_MATCH_POST_WINDOW_MS
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy.PENDING_THREAD_MATCH_PRE_WINDOW_MS
import com.intellij.agent.workbench.sessions.core.AgentSessionThreadRebindPolicy.PENDING_THREAD_NO_BASELINE_AUTO_BIND_MAX_AGE_MS
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
private const val PENDING_THREAD_AMBIGUITY_NOTIFY_AFTER_POLLS = 2
private const val PENDING_THREAD_AMBIGUITY_NOTIFY_COOLDOWN_MS = 5 * 60 * 1000L
private const val PROVIDER_REFRESH_HINT_MAX_LISTED_THREAD_IDS_PER_PATH = 200

internal class AgentSessionThreadRebindSupport(
  private val provider: AgentSessionProvider,
  internal val canBindPendingOpenChatTabs: Boolean,
  private val canRebindConcreteNewThreads: Boolean,
  private val openAgentChatPendingTabsBinder: suspend (
    AgentSessionProvider,
    Map<String, List<AgentChatPendingTabRebindRequest>>,
  ) -> AgentChatPendingTabRebindReport,
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
    openConcreteThreadIdentitiesByPath: Map<String, Set<String>> = emptyMap(),
  ): Map<String, Set<String>> {
    if (targetPaths.isEmpty()) {
      return emptyMap()
    }
    val effectivePendingTabsByPath = if (canBindPendingOpenChatTabs) pendingTabsByPath else emptyMap()

    val hintThreadIdsByPath = LinkedHashMap<String, LinkedHashSet<String>>()
    for (path in targetPaths) {
      val baselineKnownThreadIds = knownThreadIdsByPath[path].orEmpty()
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
        .forEach { thread -> ids.add(thread.id) }

      effectivePendingTabsByPath[path]
        .orEmpty()
        .forEach { pendingTab ->
          val identity = parseAgentSessionIdentity(pendingTab.pendingThreadIdentity) ?: return@forEach
          if (identity.provider != provider || isAgentSessionNewSessionId(identity.sessionId)) {
            return@forEach
          }
          ids.add(identity.sessionId)
        }

      if (ids.isNotEmpty() || effectivePendingTabsByPath[path]?.isNotEmpty() == true) {
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

  fun applyPresentationHints(
    outcomes: MutableMap<String, ProviderRefreshOutcome>,
    refreshHintsByPath: Map<String, AgentSessionRefreshHints>,
  ) {
    if (outcomes.isEmpty() || refreshHintsByPath.isEmpty()) {
      return
    }

    val updatedOutcomes = LinkedHashMap<String, ProviderRefreshOutcome>()
    for ((path, outcome) in outcomes) {
      val threads = outcome.threads ?: continue
      val refreshHints = refreshHintsByPath[path] ?: continue
      val presentationUpdatesByThreadId = refreshHints.resolvePresentationUpdatesByThreadId()
      if (presentationUpdatesByThreadId.isEmpty()) {
        continue
      }

      var changed = false
      val updatedThreads = threads.map { thread ->
        if (thread.provider != provider) {
          return@map thread
        }
        val presentationUpdate = presentationUpdatesByThreadId[thread.id] ?: return@map thread
        val resolvedUpdate = resolveAgentThreadPresentationUpdate(
          thread = thread,
          presentationUpdate = presentationUpdate,
        )
        if (resolvedUpdate.title == thread.title && resolvedUpdate.activityReport == thread.activityReport && resolvedUpdate.updatedAt == thread.updatedAt) {
          return@map thread
        }
        changed = true
        thread.copy(title = resolvedUpdate.title, activityReport = resolvedUpdate.activityReport, updatedAt = resolvedUpdate.updatedAt)
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
  ) {
    if (!canBindPendingOpenChatTabs || pendingTabsByPath.isEmpty()) {
      clearPendingThreadAmbiguityState()
      return
    }
    val eligiblePendingTabsByPath = selectPendingTabsEligibleForRebind(
      pendingTabsByPath = pendingTabsByPath,
      allowedThreadIdsByPath = allowedThreadIdsByPath,
      nowMs = System.currentTimeMillis(),
    )
    if (eligiblePendingTabsByPath.isEmpty()) {
      clearPendingThreadAmbiguityState()
      return
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
          buildAgentSessionChatRebindTarget(
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
      for ((threadId, title, updatedAt, activity) in rebindCandidates) {
        pathCandidates.add(
          buildAgentSessionChatRebindTarget(
            path = path,
            provider = provider,
            threadId = threadId,
            title = title,
            activity = activity,
            updatedAt = updatedAt,
          )
        )
      }
    }

    if (candidatesByPath.isEmpty()) {
      return
    }

    val matchResult = PendingAgentChatTabMatcher.match(
      pendingTabsByPath = eligiblePendingTabsByPath,
      candidatesByPath = candidatesByPath,
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
      return
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

    return
  }

  suspend fun clearStaleConcreteOpenChatNewThreadRebindAnchors(
    refreshId: Long,
    concreteTabsByPath: Map<String, List<AgentChatConcreteTabSnapshot>> = emptyMap(),
  ) {
    if (!canRebindConcreteNewThreads || concreteTabsByPath.isEmpty()) {
      return
    }

    val nowMs = System.currentTimeMillis()
    val staleTabsByPath = LinkedHashMap<String, List<AgentChatConcreteTabSnapshot>>()
    for ((path, tabs) in concreteTabsByPath) {
      if (tabs.isEmpty()) {
        continue
      }
      val staleTabs = tabs.filterNot { tab ->
        isConcreteCodexNewThreadRebindAnchorActive(
          rebindRequestedAtMs = tab.newThreadRebindRequestedAtMs,
          currentTimeMs = nowMs,
        )
      }
      if (staleTabs.isNotEmpty()) {
        staleTabsByPath[path] = staleTabs
      }
    }

    if (staleTabsByPath.isEmpty()) {
      return
    }
    val cleared = withContext(Dispatchers.UI) {
      clearOpenConcreteNewThreadRebindAnchors(provider, staleTabsByPath)
    }
    LOG.debug {
      "Provider refresh id=$refreshId provider=${provider.value} cleared stale /new tab anchors " +
      "(paths=${staleTabsByPath.size}, cleared=$cleared)"
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

}

private data class PendingThreadAmbiguityState(
  @JvmField val pollCount: Int,
  @JvmField val lastWarnedAtMs: Long?,
)

private fun AgentChatPendingTabSnapshot.isEligibleForNoBaselineAutoBind(nowMs: Long): Boolean {
  val createdAtMs = pendingCreatedAtMs ?: return false
  return !pendingLaunchMode.isNullOrBlank() &&
         nowMs >= createdAtMs &&
         nowMs - createdAtMs <= PENDING_THREAD_NO_BASELINE_AUTO_BIND_MAX_AGE_MS
}

internal fun buildAgentSessionChatRebindTarget(
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
