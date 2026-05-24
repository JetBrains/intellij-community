// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.cost

import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterPriceSnapshot(
  @JvmField val fetchedAt: Long,
  @JvmField val entries: List<OpenRouterPriceEntry> = emptyList(),
)

@Serializable
data class OpenRouterPriceEntry(
  @JvmField val id: String,
  @JvmField val canonicalSlug: String? = null,
  @JvmField val displayName: String? = null,
  @JvmField val normalizedNames: Set<String> = emptySet(),
  @JvmField val promptTokenPriceUsd: String? = null,
  @JvmField val completionTokenPriceUsd: String? = null,
  @JvmField val cacheReadTokenPriceUsd: String? = null,
  @JvmField val cacheWriteTokenPriceUsd: String? = null,
)

fun matchOpenRouterModel(modelId: String?, snapshot: OpenRouterPriceSnapshot): OpenRouterPriceEntry? {
  val normalizedNames = buildNormalizedModelNames(modelId)
  if (normalizedNames.isEmpty()) return null

  val exactMatches = snapshot.entries.filter { entry -> entry.normalizedNames.any(normalizedNames::contains) }
    .distinctBy(OpenRouterPriceEntry::id)
  if (exactMatches.size == 1) {
    return exactMatches.single()
  }

  val prefixMatches = snapshot.entries.filter { entry ->
    entry.normalizedNames.any { candidate -> normalizedNames.any { query -> query.startsWith(candidate) || candidate.startsWith(query) } }
  }.distinctBy(OpenRouterPriceEntry::id)
  return prefixMatches.singleOrNull()
}

internal fun createOpenRouterPriceEntry(
  id: String,
  canonicalSlug: String?,
  displayName: String?,
  promptTokenPriceUsd: String?,
  completionTokenPriceUsd: String?,
  cacheReadTokenPriceUsd: String?,
  cacheWriteTokenPriceUsd: String?,
): OpenRouterPriceEntry {
  return OpenRouterPriceEntry(
    id = id,
    canonicalSlug = canonicalSlug,
    displayName = displayName,
    normalizedNames = linkedSetOf<String>().apply {
      addAll(buildNormalizedModelNames(id))
      addAll(buildNormalizedModelNames(canonicalSlug))
      addAll(buildNormalizedModelNames(displayName))
    },
    promptTokenPriceUsd = promptTokenPriceUsd,
    completionTokenPriceUsd = completionTokenPriceUsd,
    cacheReadTokenPriceUsd = cacheReadTokenPriceUsd,
    cacheWriteTokenPriceUsd = cacheWriteTokenPriceUsd,
  )
}

internal fun buildNormalizedModelNames(value: String?): Set<String> {
  if (value.isNullOrBlank()) return emptySet()

  val trimmed = value.trim()
  val slashSeparatedParts = trimmed.split('/').filter { it.isNotBlank() }
  val candidates = LinkedHashSet<String>()
  candidates += trimmed
  for (index in slashSeparatedParts.indices) {
    candidates += slashSeparatedParts.subList(index, slashSeparatedParts.size).joinToString("/")
  }
  return candidates.mapNotNull(::normalizeOpenRouterModelName).toCollection(LinkedHashSet())
}

internal fun normalizeOpenRouterModelName(value: String?): String? {
  if (value.isNullOrBlank()) return null

  val normalized = buildString(value.length) {
    var lastWasSeparator = false
    for (ch in value.lowercase()) {
      if (ch.isLetterOrDigit()) {
        append(ch)
        lastWasSeparator = false
      }
      else if (!lastWasSeparator) {
        append('-')
        lastWasSeparator = true
      }
    }
  }.trim('-')

  return normalized.ifBlank { null }
}
