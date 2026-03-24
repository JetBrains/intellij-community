// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.threadDumpParser

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmName

/**
 * Inline metadata key that stores the synthetic item kind, for example a thread container or a coroutine.
 */
@ApiStatus.Internal
const val TYPE_KEY: String = "type"

/**
 * Inline metadata key that stores the stable identifier of an exported dump item.
 */
@ApiStatus.Internal
const val ID_KEY: String = "id"

/**
 * Inline metadata key that stores the identifier of the exported parent item.
 */
@ApiStatus.Internal
const val PARENT_ID_KEY: String = "parentId"

private val metadataJson: Json = Json

/**
 * Parses a trailing inline metadata block from a thread header.
 *
 * The exported wire format keeps metadata as a JSON object, but replaces the outer `{` and `}` with `[` and `]`.
 * This is needed for the older [ThreadDumpParser]. It skips everything in `[` `]` blocks.
 */
internal fun parseMetadataSuffix(header: String): Map<String, String>? {
  val suffix = findMetadataSuffix(header) ?: return null
  return suffix.metadata
}

internal fun stripMetadataSuffix(header: String): String {
  val suffix = findMetadataSuffix(header) ?: return header
  return header.substring(0, suffix.startOffset).trimEnd()
}

private fun findMetadataSuffix(header: String): MetadataSuffix? {
  if (header.isEmpty() || header.last() != ']') {
    return null
  }

  var blockStart = header.lastIndexOf('[')
  while (blockStart >= 0) {
    val metadataContent = header.substring(blockStart + 1, header.length - 1)
    val metadata = parseJsonMetadataContent(metadataContent)
    if (!metadata.isNullOrEmpty()) {
      return MetadataSuffix(metadata, blockStart)
    }
    blockStart = header.lastIndexOf('[', blockStart - 1)
  }
  return null
}

/**
 * Restores the exported `[...]` wrapper back to a regular JSON object and parses it as flat metadata.
 */
private fun parseJsonMetadataContent(content: String): Map<String, String>? {
  if (content.isBlank()) {
    return null
  }
  // The exported format keeps the outer wrapper as [...] so we temporarily restore a JSON object here.
  val objectText = "{$content}"
  val root = try {
    metadataJson.parseToJsonElement(objectText) as? JsonObject
  }
  catch (_: Exception) {
    return null
  }
  if (root == null) {
    return null
  }

  val result = mutableMapOf<String, String>()
  for ((key, value) in root) {
    val scalarValue = value.asMetadataString() ?: return null
    result[key] = scalarValue
  }
  return result.takeIf { it.isNotEmpty() }
}

private fun JsonElement.asMetadataString(): String? {
  // Inline metadata is intentionally flat. Nested JSON would complicate suffix parsing and isn't needed today.
  val primitive = this as? JsonPrimitive ?: return null
  if (primitive === JsonNull) {
    return null
  }
  return primitive.content
}

/**
 * Parsed trailing metadata block together with the offset where it starts in the thread header.
 */
private data class MetadataSuffix(
  val metadata: Map<String, String>,
  val startOffset: Int,
)
