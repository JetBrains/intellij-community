// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.codex

import com.intellij.agent.workbench.chat.AgentChatPendingCodexTabSnapshot
import com.intellij.agent.workbench.chat.AgentChatPendingTabRebindTarget

internal data class CodexPendingTabBinding(
  val pendingTabKey: String,
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

    val bindings = LinkedHashMap<String, AgentChatPendingTabRebindTarget>()
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
        CodexPendingTabBinding(
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
