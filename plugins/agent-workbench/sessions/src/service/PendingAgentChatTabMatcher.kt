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

internal data class TimestampedRebindSubject(
  @JvmField val key: String,
  @JvmField val timestampMs: Long?,
)

internal data class TimestampedRebindCandidate<T>(
  @JvmField val identity: String,
  @JvmField val updatedAtMs: Long,
  @JvmField val value: T,
)

internal data class TimestampedRebindBinding<T>(
  @JvmField val subjectKey: String,
  @JvmField val candidate: T,
)

internal data class TimestampedRebindMatchResult<T>(
  @JvmField val bindings: List<TimestampedRebindBinding<T>>,
  @JvmField val ambiguousSubjectKeys: Set<String>,
  @JvmField val noMatchSubjectKeys: Set<String>,
)

internal object TimestampedRebindMatcher {
  fun <T> match(
    subjects: List<TimestampedRebindSubject>,
    candidates: List<TimestampedRebindCandidate<T>>,
    unavailableCandidateIdentities: Set<String> = emptySet(),
    preWindowMs: Long,
    postWindowMs: Long,
  ): TimestampedRebindMatchResult<T> {
    if (subjects.isEmpty() || candidates.isEmpty()) {
      return TimestampedRebindMatchResult(
        bindings = emptyList(),
        ambiguousSubjectKeys = emptySet(),
        noMatchSubjectKeys = subjects.mapTo(LinkedHashSet(), TimestampedRebindSubject::key),
      )
    }

    val uniqueSubjects = subjects
      .asSequence()
      .distinctBy { it.key }
      .toList()
    val subjectByKey = uniqueSubjects.associateBy { it.key }
    val candidateByIdentity = deduplicateCandidates(candidates, unavailableCandidateIdentities)
    val subjectEdges = LinkedHashMap<String, LinkedHashSet<String>>(uniqueSubjects.size)
    val candidateEdges = LinkedHashMap<String, LinkedHashSet<String>>(candidateByIdentity.size)
    val initialEdgeCounts = LinkedHashMap<String, Int>(uniqueSubjects.size)

    for (subject in uniqueSubjects) {
      val connectedCandidates = LinkedHashSet<String>()
      val timestamp = subject.timestampMs
      if (timestamp != null) {
        val minTimestamp = timestamp - preWindowMs
        val maxTimestamp = timestamp + postWindowMs
        for ((candidateIdentity, candidate) in candidateByIdentity) {
          val updatedAt = candidate.updatedAtMs
          if (updatedAt <= 0L) {
            continue
          }
          if (updatedAt in minTimestamp..maxTimestamp) {
            connectedCandidates.add(candidateIdentity)
            candidateEdges.getOrPut(candidateIdentity) { LinkedHashSet() }.add(subject.key)
          }
        }
      }
      subjectEdges[subject.key] = connectedCandidates
      initialEdgeCounts[subject.key] = connectedCandidates.size
    }

    val bindings = LinkedHashMap<String, T>()
    while (true) {
      val forcedPairs = subjectEdges.entries
        .asSequence()
        .filter { it.value.size == 1 }
        .map { it.key to it.value.first() }
        .filter { (subjectKey, candidateIdentity) ->
          candidateEdges[candidateIdentity]?.let { subjectKeys -> subjectKeys.size == 1 && subjectKey in subjectKeys } == true
        }
        .toList()
      if (forcedPairs.isEmpty()) {
        break
      }

      for ((subjectKey, candidateIdentity) in forcedPairs) {
        if (subjectKey !in subjectEdges || candidateIdentity !in candidateEdges) {
          continue
        }
        val target = candidateByIdentity[candidateIdentity] ?: continue
        bindings[subjectKey] = target.value

        subjectEdges.remove(subjectKey)
        candidateEdges.remove(candidateIdentity)
        subjectEdges.values.forEach { it.remove(candidateIdentity) }
        candidateEdges.values.forEach { it.remove(subjectKey) }
      }
    }

    val ambiguousSubjectKeys = LinkedHashSet<String>()
    val noMatchSubjectKeys = LinkedHashSet<String>()
    for ((subjectKey, remainingEdges) in subjectEdges) {
      if (subjectKey !in subjectByKey) continue
      val initialEdgeCount = initialEdgeCounts[subjectKey] ?: 0
      if (remainingEdges.isNotEmpty() || initialEdgeCount > 0) {
        ambiguousSubjectKeys.add(subjectKey)
      }
      else {
        noMatchSubjectKeys.add(subjectKey)
      }
    }

    return TimestampedRebindMatchResult(
      bindings = bindings.entries
        .sortedBy { it.key }
        .map { (subjectKey, candidate) -> TimestampedRebindBinding(subjectKey = subjectKey, candidate = candidate) },
      ambiguousSubjectKeys = ambiguousSubjectKeys,
      noMatchSubjectKeys = noMatchSubjectKeys,
    )
  }

  private fun <T> deduplicateCandidates(
    candidates: List<TimestampedRebindCandidate<T>>,
    unavailableCandidateIdentities: Set<String>,
  ): Map<String, TimestampedRebindCandidate<T>> {
    val result = LinkedHashMap<String, TimestampedRebindCandidate<T>>()
    for (candidate in candidates) {
      if (candidate.identity in unavailableCandidateIdentities) {
        continue
      }
      val existing = result[candidate.identity]
      if (existing == null || candidate.updatedAtMs >= existing.updatedAtMs) {
        result[candidate.identity] = candidate
      }
    }
    return result
  }
}

internal object PendingAgentChatTabMatcher {
  fun match(
    pendingTabsByPath: Map<String, List<AgentChatPendingTabSnapshot>>,
    candidatesByPath: Map<String, List<AgentChatTabRebindTarget>>,
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
      val pathResult = matchPath(
        pendingTabs = pendingTabs,
        candidates = pathCandidates,
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
    preWindowMs: Long,
    postWindowMs: Long,
  ): PathMatchResult {
    val uniquePendingTabs = pendingTabs
      .asSequence()
      .distinctBy { it.pendingTabKey }
      .toList()
    val pendingTabByKey = uniquePendingTabs.associateBy { it.pendingTabKey }
    val matchResult = TimestampedRebindMatcher.match(
      subjects = uniquePendingTabs.map { pendingTab ->
        TimestampedRebindSubject(
          key = pendingTab.pendingTabKey,
          timestampMs = pendingTab.pendingFirstInputAtMs ?: pendingTab.pendingCreatedAtMs,
        )
      },
      candidates = candidates.map { candidate ->
        TimestampedRebindCandidate(
          identity = candidate.threadIdentity,
          updatedAtMs = candidate.threadUpdatedAt,
          value = candidate,
        )
      },
      preWindowMs = preWindowMs,
      postWindowMs = postWindowMs,
    )

    val ambiguousPendingIdentities = matchResult.ambiguousSubjectKeys
      .mapNotNullTo(LinkedHashSet()) { pendingTabKey -> pendingTabByKey[pendingTabKey]?.pendingThreadIdentity }
    val noMatchPendingIdentities = matchResult.noMatchSubjectKeys
      .mapNotNullTo(LinkedHashSet()) { pendingTabKey -> pendingTabByKey[pendingTabKey]?.pendingThreadIdentity }

    val orderedBindings = matchResult.bindings
      .mapNotNull { binding ->
        val pendingTabKey = binding.subjectKey
        val pendingTab = pendingTabByKey[pendingTabKey] ?: return@mapNotNull null
        PendingTabBinding(
          pendingTabKey = pendingTabKey,
          pendingThreadIdentity = pendingTab.pendingThreadIdentity,
          target = binding.candidate,
        )
      }

    return PathMatchResult(
      bindings = orderedBindings,
      ambiguousPendingThreadIdentities = ambiguousPendingIdentities,
      noMatchPendingThreadIdentities = noMatchPendingIdentities,
    )
  }

  private data class PathMatchResult(
    @JvmField val bindings: List<PendingTabBinding>,
    @JvmField val ambiguousPendingThreadIdentities: Set<String>,
    @JvmField val noMatchPendingThreadIdentities: Set<String>,
  )
}
