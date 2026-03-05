// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.dynatrace.hash4j.hashing.HashSink
import com.dynatrace.hash4j.hashing.HashValue128
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextItem
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayloadValue

internal fun computeContextFingerprint(items: List<AgentPromptContextItem>): HashValue128? {
  if (items.isEmpty()) {
    return null
  }

  val hash = Hashing.xxh3_128().hashStream()
  // version
  hash.putInt(0)

  for (item in items) {
    hash.putString(item.rendererId)
    appendField(hash, "title", item.title)
    hash.putString(item.body)
    appendField(hash, "itemId", item.itemId)
    appendField(hash, "parentItemId", item.parentItemId)
    hash.putString(item.source)
    hash.putInt(item.phase?.ordinal ?: -1)
    hash.putInt(item.truncation.originalChars)
    hash.putInt(item.truncation.includedChars)
    hash.putInt(item.truncation.reason.ordinal)
    appendPayloadCanonical(hash, item.payload)
  }
  hash.putInt(items.size)
  return hash.get()
}

internal fun applyDraftContextRemovals(
  entries: List<ContextEntry>,
  currentFingerprint: HashValue128?,
  draftFingerprint: HashValue128?,
  removedLogicalItemIds: Collection<String>,
): List<ContextEntry> {
  if (entries.isEmpty() || currentFingerprint == null || draftFingerprint == null || currentFingerprint != draftFingerprint) {
    return entries
  }
  val normalizedRemovedIds = normalizeRemovedContextItemIds(removedLogicalItemIds)
  if (normalizedRemovedIds.isEmpty()) {
    return entries
  }

  var remaining = entries
  normalizedRemovedIds.forEach { removedLogicalItemId ->
    val entryId = remaining.firstOrNull { entry -> entry.logicalItemId == removedLogicalItemId }?.id ?: return@forEach
    remaining = resolveContextEntriesAfterRemoval(remaining, removedEntryId = entryId)
  }
  return remaining
}

internal fun collectRemovedLogicalItemIds(
  beforeEntries: List<ContextEntry>,
  afterEntries: List<ContextEntry>,
): Set<String> {
  if (beforeEntries.isEmpty()) {
    return emptySet()
  }
  val remainingLogicalIds = afterEntries
    .asSequence()
    .mapNotNull(ContextEntry::logicalItemId)
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toHashSet()
  val removedLogicalIds = LinkedHashSet<String>()
  for (entry in beforeEntries) {
    val logicalItemId = entry.logicalItemId?.trim()?.takeIf { it.isNotEmpty() } ?: continue
    if (logicalItemId !in remainingLogicalIds) {
      removedLogicalIds.add(logicalItemId)
    }
  }
  return removedLogicalIds
}

internal fun normalizeRemovedContextItemIds(ids: Collection<String>): List<String> {
  if (ids.isEmpty()) {
    return emptyList()
  }
  val normalized = LinkedHashSet<String>()
  for (rawId in ids) {
    val id = rawId.trim()
    if (id.isNotEmpty()) {
      normalized.add(id)
    }
  }
  return normalized.toList()
}

private fun appendField(sink: HashSink, name: String, value: String?) {
  sink.putString(name)
  if (value == null) {
    sink.putInt(-1)
  }
  else {
    sink.putString(value)
  }
}

private fun appendPayloadCanonical(sink: HashSink, payload: AgentPromptPayloadValue) {
  when (payload) {
    is AgentPromptPayloadValue.Obj -> {
      sink.putByte(0)
      sink.putUnorderedIterable(payload.fields.keys, { key, sink ->
        sink.putString(key)
        appendPayloadCanonical(sink, payload.fields.getValue(key))
      }, Hashing.xxh3_64())
    }

    is AgentPromptPayloadValue.Arr -> {
      sink.putByte(1)
      sink.putOrderedIterable(payload.items) { key, sink ->
        appendPayloadCanonical(sink, key)
      }
    }

    is AgentPromptPayloadValue.Str -> {
      sink.putByte(2)
      sink.putString(payload.value)
    }

    is AgentPromptPayloadValue.Num -> {
      sink.putByte(3)
      sink.putString(payload.value)
    }

    is AgentPromptPayloadValue.Bool -> {
      sink.putByte(4)
      sink.putBoolean(payload.value)
    }

    AgentPromptPayloadValue.Null -> sink.putByte(5)
  }
}