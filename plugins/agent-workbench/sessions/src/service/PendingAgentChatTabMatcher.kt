// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatPendingTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatTabRebindTarget

internal data class PendingTabBinding(
  @JvmField val pendingTabKey: String,
  @JvmField val pendingThreadIdentity: String,
  @JvmField val target: AgentChatTabRebindTarget,
)

internal data class PendingTabMatchResult(
  @JvmField val bindingsByPath: Map<String, List<PendingTabBinding>>,
  @JvmField val ambiguousPendingThreadIdentitiesByPath: Map<String, Set<String>>,
  @JvmField val noMatchPendingThreadIdentitiesByPath: Map<String, Set<String>>,
)

internal object PendingAgentChatTabMatcher {
  fun match(
      pendingTabsByPath: Map<String, List<AgentChatPendingTabSnapshot>>,
      candidatesByPath: Map<String, List<AgentChatTabRebindTarget>>,
      openConcreteIdentitiesByPath: Map<String, Set<String>>,
      preWindowMs: Long,
      postWindowMs: Long,
  ): PendingTabMatchResult {
    val bindingsByPath = LinkedHashMap<String, List<PendingTabBinding>>()
    val ambiguousByPath = LinkedHashMap<String, Set<String>>()
    val noMatchByPath = LinkedHashMap<String, Set<String>>()

    for ((path, pendingTabs) in pendingTabsByPath) {
      if (pendingTabs.isEmpty()) {
        continue
      }
      val pathCandidates = candidatesByPath[path].orEmpty()
      val concreteIdentities = openConcreteIdentitiesByPath[path].orEmpty()
      val pathResult = matchPath(
        pendingTabs = pendingTabs,
        candidates = pathCandidates,
        openConcreteIdentities = concreteIdentities,
        preWindowMs = preWindowMs,
        postWindowMs = postWindowMs,
      )
      if (pathResult.bindings.isNotEmpty()) {
        bindingsByPath[path] = pathResult.bindings
      }
      if (pathResult.ambiguousPendingThreadIdentities.isNotEmpty()) {
        ambiguousByPath[path] = pathResult.ambiguousPendingThreadIdentities
      }
      if (pathResult.noMatchPendingThreadIdentities.isNotEmpty()) {
        noMatchByPath[path] = pathResult.noMatchPendingThreadIdentities
      }
    }

    return PendingTabMatchResult(
      bindingsByPath = bindingsByPath,
      ambiguousPendingThreadIdentitiesByPath = ambiguousByPath,
      noMatchPendingThreadIdentitiesByPath = noMatchByPath,
    )
  }

  private fun matchPath(
      pendingTabs: List<AgentChatPendingTabSnapshot>,
      candidates: List<AgentChatTabRebindTarget>,
      openConcreteIdentities: Set<String>,
      preWindowMs: Long,
      postWindowMs: Long,
  ): PathMatchResult {
    val uniquePendingTabs = pendingTabs
      .asSequence()
      .distinctBy { it.pendingTabKey }
      .toList()
    val pendingTabByKey = uniquePendingTabs.associateBy { it.pendingTabKey }
    val candidateByIdentity = deduplicateCandidates(candidates)
      .filterKeys { it !in openConcreteIdentities }
    val pendingEdges = LinkedHashMap<String, LinkedHashSet<String>>(uniquePendingTabs.size)
    val candidateEdges = LinkedHashMap<String, LinkedHashSet<String>>(candidateByIdentity.size)
    val initialEdgeCounts = LinkedHashMap<String, Int>(uniquePendingTabs.size)

    for (pendingTab in uniquePendingTabs) {
      val baseTimestamp = pendingTab.pendingFirstInputAtMs ?: pendingTab.pendingCreatedAtMs
      val connectedCandidates = LinkedHashSet<String>()
      if (baseTimestamp != null) {
        val minTimestamp = baseTimestamp - preWindowMs
        val maxTimestamp = baseTimestamp + postWindowMs
        for ((candidateIdentity, candidate) in candidateByIdentity) {
          val updatedAt = candidate.threadUpdatedAt
          if (updatedAt <= 0L) {
            continue
          }
          if (updatedAt in minTimestamp..maxTimestamp) {
            connectedCandidates.add(candidateIdentity)
            candidateEdges.getOrPut(candidateIdentity) { LinkedHashSet() }.add(pendingTab.pendingTabKey)
          }
        }
      }
      pendingEdges[pendingTab.pendingTabKey] = connectedCandidates
      initialEdgeCounts[pendingTab.pendingTabKey] = connectedCandidates.size
    }

    val bindings = LinkedHashMap<String, AgentChatTabRebindTarget>()
    while (true) {
      val forcedPairs = pendingEdges.entries
        .asSequence()
        .filter { it.value.size == 1 }
        .map { it.key to it.value.first() }
        .filter { (pendingTabKey, candidateIdentity) ->
          candidateEdges[candidateIdentity]?.let { pendingSet -> pendingSet.size == 1 && pendingTabKey in pendingSet } == true
        }
        .toList()
      if (forcedPairs.isEmpty()) {
        break
      }

      for ((pendingTabKey, candidateIdentity) in forcedPairs) {
        if (pendingTabKey !in pendingEdges || candidateIdentity !in candidateEdges) {
          continue
        }
        val target = candidateByIdentity[candidateIdentity] ?: continue
        bindings[pendingTabKey] = target

        pendingEdges.remove(pendingTabKey)
        candidateEdges.remove(candidateIdentity)
        pendingEdges.values.forEach { it.remove(candidateIdentity) }
        candidateEdges.values.forEach { it.remove(pendingTabKey) }
      }
    }

    val ambiguousPendingIdentities = LinkedHashSet<String>()
    val noMatchPendingIdentities = LinkedHashSet<String>()
    for ((pendingTabKey, remainingEdges) in pendingEdges) {
      val pendingTab = pendingTabByKey[pendingTabKey] ?: continue
      val initialEdgeCount = initialEdgeCounts[pendingTabKey] ?: 0
      if (remainingEdges.isNotEmpty() || initialEdgeCount > 0) {
        ambiguousPendingIdentities.add(pendingTab.pendingThreadIdentity)
      }
      else {
        noMatchPendingIdentities.add(pendingTab.pendingThreadIdentity)
      }
    }

    val orderedBindings = bindings.entries
      .sortedBy { it.key }
      .mapNotNull { (pendingTabKey, target) ->
        val pendingTab = pendingTabByKey[pendingTabKey] ?: return@mapNotNull null
        PendingTabBinding(
          pendingTabKey = pendingTabKey,
          pendingThreadIdentity = pendingTab.pendingThreadIdentity,
          target = target,
        )
      }

    return PathMatchResult(
      bindings = orderedBindings,
      ambiguousPendingThreadIdentities = ambiguousPendingIdentities,
      noMatchPendingThreadIdentities = noMatchPendingIdentities,
    )
  }

  private fun deduplicateCandidates(candidates: List<AgentChatTabRebindTarget>): Map<String, AgentChatTabRebindTarget> {
    val result = LinkedHashMap<String, AgentChatTabRebindTarget>()
    for (candidate in candidates) {
      val existing = result[candidate.threadIdentity]
      if (existing == null || candidate.threadUpdatedAt >= existing.threadUpdatedAt) {
        result[candidate.threadIdentity] = candidate
      }
    }
    return result
  }

  private data class PathMatchResult(
    @JvmField val bindings: List<PendingTabBinding>,
    @JvmField val ambiguousPendingThreadIdentities: Set<String>,
    @JvmField val noMatchPendingThreadIdentities: Set<String>,
  )
}
