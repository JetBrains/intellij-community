// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindTarget

internal data class CodexPendingTabBinding(
  val pendingThreadIdentity: String,
  val target: AgentChatPendingTabRebindTarget,
)

internal data class CodexPendingTabMatchResult(
  val bindingsByPath: Map<String, List<CodexPendingTabBinding>>,
  val ambiguousPendingThreadIdentitiesByPath: Map<String, Set<String>>,
  val noMatchPendingThreadIdentitiesByPath: Map<String, Set<String>>,
)

internal object CodexPendingTabMatcher {
  fun match(
    pendingTabsByPath: Map<String, List<AgentChatPendingCodexTabSnapshot>>,
    candidatesByPath: Map<String, List<AgentChatPendingTabRebindTarget>>,
    openConcreteIdentitiesByPath: Map<String, Set<String>>,
    preWindowMs: Long,
    postWindowMs: Long,
  ): CodexPendingTabMatchResult {
    val bindingsByPath = LinkedHashMap<String, List<CodexPendingTabBinding>>()
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

    return CodexPendingTabMatchResult(
      bindingsByPath = bindingsByPath,
      ambiguousPendingThreadIdentitiesByPath = ambiguousByPath,
      noMatchPendingThreadIdentitiesByPath = noMatchByPath,
    )
  }

  private fun matchPath(
    pendingTabs: List<AgentChatPendingCodexTabSnapshot>,
    candidates: List<AgentChatPendingTabRebindTarget>,
    openConcreteIdentities: Set<String>,
    preWindowMs: Long,
    postWindowMs: Long,
  ): PathMatchResult {
    val uniquePendingTabs = pendingTabs
      .asSequence()
      .distinctBy { it.pendingThreadIdentity }
      .toList()
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
            candidateEdges.getOrPut(candidateIdentity) { LinkedHashSet() }.add(pendingTab.pendingThreadIdentity)
          }
        }
      }
      pendingEdges[pendingTab.pendingThreadIdentity] = connectedCandidates
      initialEdgeCounts[pendingTab.pendingThreadIdentity] = connectedCandidates.size
    }

    val bindings = LinkedHashMap<String, AgentChatPendingTabRebindTarget>()
    while (true) {
      val forcedPairs = pendingEdges.entries
        .asSequence()
        .filter { it.value.size == 1 }
        .map { it.key to it.value.first() }
        .filter { (pendingIdentity, candidateIdentity) ->
          candidateEdges[candidateIdentity]?.let { pendingSet -> pendingSet.size == 1 && pendingIdentity in pendingSet } == true
        }
        .toList()
      if (forcedPairs.isEmpty()) {
        break
      }

      for ((pendingIdentity, candidateIdentity) in forcedPairs) {
        if (pendingIdentity !in pendingEdges || candidateIdentity !in candidateEdges) {
          continue
        }
        val target = candidateByIdentity[candidateIdentity] ?: continue
        bindings[pendingIdentity] = target

        pendingEdges.remove(pendingIdentity)
        candidateEdges.remove(candidateIdentity)
        pendingEdges.values.forEach { it.remove(candidateIdentity) }
        candidateEdges.values.forEach { it.remove(pendingIdentity) }
      }
    }

    val ambiguousPendingIdentities = LinkedHashSet<String>()
    val noMatchPendingIdentities = LinkedHashSet<String>()
    for ((pendingIdentity, remainingEdges) in pendingEdges) {
      val initialEdgeCount = initialEdgeCounts[pendingIdentity] ?: 0
      if (remainingEdges.isNotEmpty() || initialEdgeCount > 0) {
        ambiguousPendingIdentities.add(pendingIdentity)
      }
      else {
        noMatchPendingIdentities.add(pendingIdentity)
      }
    }

    val orderedBindings = bindings.entries
      .sortedBy { it.key }
      .map { (pendingIdentity, target) ->
        CodexPendingTabBinding(
          pendingThreadIdentity = pendingIdentity,
          target = target,
        )
      }

    return PathMatchResult(
      bindings = orderedBindings,
      ambiguousPendingThreadIdentities = ambiguousPendingIdentities,
      noMatchPendingThreadIdentities = noMatchPendingIdentities,
    )
  }

  private fun deduplicateCandidates(candidates: List<AgentChatPendingTabRebindTarget>): Map<String, AgentChatPendingTabRebindTarget> {
    val result = LinkedHashMap<String, AgentChatPendingTabRebindTarget>()
    for (candidate in candidates) {
      val existing = result[candidate.threadIdentity]
      if (existing == null || candidate.threadUpdatedAt >= existing.threadUpdatedAt) {
        result[candidate.threadIdentity] = candidate
      }
    }
    return result
  }

  private data class PathMatchResult(
    val bindings: List<CodexPendingTabBinding>,
    val ambiguousPendingThreadIdentities: Set<String>,
    val noMatchPendingThreadIdentities: Set<String>,
  )
}

