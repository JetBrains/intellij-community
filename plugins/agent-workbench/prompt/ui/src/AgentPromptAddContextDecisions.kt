// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.dynatrace.hash4j.hashing.HashSink
import com.dynatrace.hash4j.hashing.HashValue128
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem

internal fun addContextItemFingerprint(item: AgentPromptContextItem): HashValue128 {
  val hash = Hashing.xxh3_128().hashStream()
  hash.putInt(0)
  hash.putString(item.rendererId.trim())
  appendNormalizedField(hash, "source", item.source)
  appendNormalizedField(hash, "itemId", item.itemId)
  appendNormalizedField(hash, "parentItemId", item.parentItemId)
  appendNormalizedField(hash, "title", item.title)
  hash.putString(normalizeContextText(item.body))
  appendContextPayloadCanonical(hash, item.payload)
  return hash.get()
}

internal fun appendUniqueAddContextItems(
  currentItems: List<AgentPromptContextItem>,
  candidateItems: List<AgentPromptContextItem>,
): List<AgentPromptContextItem> {
  if (candidateItems.isEmpty()) {
    return emptyList()
  }

  val existingFingerprints = currentItems.mapTo(HashSet()) { item -> addContextItemFingerprint(item) }
  val addedFingerprints = HashSet<HashValue128>()
  return candidateItems.filter { item ->
    val fingerprint = addContextItemFingerprint(item)
    fingerprint !in existingFingerprints && addedFingerprints.add(fingerprint)
  }
}

private fun appendNormalizedField(sink: HashSink, name: String, value: String?) {
  sink.putString(name)
  val normalized = value?.let(::normalizeContextText)
  if (normalized == null) {
    sink.putInt(-1)
  }
  else {
    sink.putString(normalized)
  }
}

private fun normalizeContextText(value: String): String {
  return value.trim().replace(Regex("\\s+"), " ")
}
